/*
 * Copyright (C) 2016-2019 Code Defenders contributors
 *
 * This file is part of Code Defenders.
 *
 * Code Defenders is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Code Defenders is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Code Defenders. If not, see <http://www.gnu.org/licenses/>.
 */
package org.codedefenders.execution;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import javax.enterprise.inject.Alternative;

import org.apache.commons.collections.CollectionUtils;
import org.codedefenders.database.EventDAO;
import org.codedefenders.database.MultiplayerGameDAO;
import org.codedefenders.database.UserDAO;
import org.codedefenders.game.AbstractGame;
import org.codedefenders.game.Mutant;
import org.codedefenders.game.Test;
import org.codedefenders.game.multiplayer.MultiplayerGame;
import org.codedefenders.game.scoring.Scorer;
import org.codedefenders.model.Event;
import org.codedefenders.model.EventStatus;
import org.codedefenders.model.EventType;
import org.codedefenders.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.codedefenders.util.Constants.*;
import static org.codedefenders.util.Constants.COST_WON_MESSAGE;

/**
 * This is a parallel implementation of IMutationTester. Parallelism is achieved
 * by means of the injected executionService
 *
 * <p>We inject instances using {@link MutationTesterProducer}
 */
@Alternative // This disable the automatic injection so we pass dependencies via the constructor
public class ParallelMutationTester extends MutationTester //
        // This MIGHT be superfluous but I am not sure how CDI works with annotations
        implements IMutationTester {
    private ExecutorService testExecutorThreadPool;

    // TODO Move the Executor service before useMutantCoverage
    public ParallelMutationTester(BackendExecutorService backend, EventDAO eventDAO, boolean useMutantCoverage, ExecutorService testExecutorThreadPool) {
        super(backend, eventDAO, useMutantCoverage);
        this.testExecutorThreadPool = testExecutorThreadPool;
    }

    private static final Logger logger = LoggerFactory.getLogger(ParallelMutationTester.class);

    @Override
    public void runTestOnAllMultiplayerMutants(MultiplayerGame game, Test test, ArrayList<String> messages) {
        int killed = 0;
        List<Mutant> mutants = game.getAliveMutants();
        mutants.addAll(game.getMutantsMarkedEquivalentPending());
        List<Mutant> killedMutants = new ArrayList<>();

        // Acquire and release the connection
        User u = UserDAO.getUserForPlayer(test.getPlayerId());

        for (Mutant mutant : mutants) {
            if (useMutantCoverage && !test.isMutantCovered(mutant)) {
                // System.out.println("Skipping non-covered mutant "
                // + mutant.getId() + ", test " + test.getId());
                continue;
            }

            if (testVsMutant(test, mutant)) {
                killed++;
                killedMutants.add(mutant);
            }
        }

        for (Mutant mutant : mutants) {
            if (mutant.isAlive()) {
                ArrayList<Test> missedTests = new ArrayList<>();

                for (int lm : mutant.getLines()) {
                    boolean found = false;
                    for (int lc : test.getLineCoverage().getLinesCovered()) {
                        if (lc == lm) {
                            found = true;
                            missedTests.add(test);
                        }
                    }
                    if (found) {
                        break;
                    }
                }
                // mutant.setScore(Scorer.score(game, mutant, missedTests));
                // mutant.update();
                mutant.incrementScore(Scorer.score(game, mutant, missedTests));
            }
        }

        // test.setScore(Scorer.score(game, test, killedMutants));
        // test.update();
        test.incrementScore(Scorer.score(game, test, killedMutants));

        int defenderStartCostActivity = game.getDefenderStartCostActivity();
        int defenderCostActivity = game.getDefenderCostActivity();

        if (killed == 0) {
            if (mutants.size() == 0) {
                messages.add(TEST_SUBMITTED_MESSAGE);
            } else {
                messages.add(TEST_KILLED_ZERO_MESSAGE);
            }
        } else {
            Event notif = new Event(-1, game.getId(), u.getId(),
                    u.getUsername() + "&#39;s test kills " + killed + " " + "mutants.",
                    EventType.DEFENDER_KILLED_MUTANT, EventStatus.GAME, new Timestamp(System.currentTimeMillis()));

            eventDAO.insert(notif);

            if (killed == 1) {
                int resultDefender = defenderStartCostActivity + defenderCostActivity;
                if (mutants.size() == 1) {
                    messages.add(String.format(COST_WON_MESSAGE, killed));
                    messages.add(TEST_KILLED_LAST_MESSAGE);
                } else {
                    messages.add(String.format(COST_WON_MESSAGE, killed));
                    messages.add(TEST_KILLED_ONE_MESSAGE);
                }
                boolean updateDefenderCost = MultiplayerGameDAO.updateDefenderCost(game, resultDefender);
            } else {
                int resultDefender = defenderStartCostActivity + defenderCostActivity * killed;
                boolean updateDefenderCost = MultiplayerGameDAO.updateDefenderCost(game, resultDefender);
                messages.add(String.format(TEST_KILLED_N_MESSAGE, killed));
                messages.add(String.format(COST_WON_MESSAGE, killed));
            }

        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.codedefenders.execution.IMutationTester#runAllTestsOnMutant(org.
     * codedefenders.game.AbstractGame, org.codedefenders.game.Mutant,
     * java.util.ArrayList, org.codedefenders.execution.TestScheduler)
     */
    @Override
    public void runAllTestsOnMutant(AbstractGame game, Mutant mutant, ArrayList<String> messages,
                                    TestScheduler scheduler) {
        // Schedule the executable tests submitted by the defenders only (true)
        List<Test> tests = scheduler.scheduleTests(game.getTests(true));

        User u = UserDAO.getUserForPlayer(mutant.getPlayerId());
        int attackerStartCostActivity = game.getAttackerStartCostActivity();
        int attackerCostActivity = game.getAttackerCostActivity() * 2;
        int defenderStartCostActivity = game.getDefenderStartCostActivity();
        int defenderCostActivity = game.getDefenderCostActivity();

        for (Test test : tests) {
            if (useMutantCoverage && !test.isMutantCovered(mutant)) {
                logger.info("Skipping non-covered mutant " + mutant.getId() + ", test " + test.getId());
                continue;
            }

            if (testVsMutant(test, mutant)) {
                int resultDefender = defenderStartCostActivity + defenderCostActivity;
                boolean updateDefenderCost = MultiplayerGameDAO.updateDefenderCostAbstract(game, resultDefender);
                logger.info("Test {} kills mutant {}", test.getId(), mutant.getId());
                messages.add(String.format(MUTANT_KILLED_BY_TEST_MESSAGE, test.getId()));
                if (game instanceof MultiplayerGame) {
                    ArrayList<Mutant> mlist = new ArrayList<>();
                    mlist.add(mutant);
                    // test.setScore(Scorer.score((MultiplayerGame) game, test,
                    // mlist));
                    // test.update();
                    test.incrementScore(Scorer.score((MultiplayerGame) game, test, mlist));
                }

                Event notif = new Event(-1, game.getId(), UserDAO.getUserForPlayer(test.getPlayerId()).getId(),
                        u.getUsername() + "&#39;s mutant is killed", EventType.DEFENDER_KILLED_MUTANT, EventStatus.GAME,
                        new Timestamp(System.currentTimeMillis()));
                eventDAO.insert(notif);

                return; // return as soon as the first test kills the mutant we return
            }
        }

        // TODO In the original implementation (see commit
        // 4fbdc78304374ee31a06d56f8ce67ca80309e24c for example)
        // the first block and the second one are swapped. Why ?
        ArrayList<Test> missedTests = new ArrayList<>();
        if (game instanceof MultiplayerGame) {
            for (Test t : tests) {
                if (CollectionUtils.containsAny(t.getLineCoverage().getLinesCovered(), mutant.getLines())) {
                    missedTests.add(t);
                }
            }
            // mutant.setScore(1 + Scorer.score((MultiplayerGame) game, mutant,
            // missedTests));
            // mutant.update();
            mutant.incrementScore(1 + Scorer.score((MultiplayerGame) game, mutant, missedTests));
        }
        int nbRelevantTests = missedTests.size();
        int TotalMutantsCreated = mutant.getTotalMutantsCreated();
        // Mutant survived
        if (nbRelevantTests == 0) {
            int result = attackerStartCostActivity - (attackerCostActivity * TotalMutantsCreated);
            boolean updateAttackerCost2 = MultiplayerGameDAO.updateAttackerCostAbstract(game, result);
            messages.add(MUTANT_SUBMITTED_MESSAGE);
        } else if (nbRelevantTests <= 1) {
            int resultDefender = attackerStartCostActivity + attackerCostActivity;
            boolean updateAttackerCost2 = MultiplayerGameDAO.updateAttackerCostAbstract(game, resultDefender);
            messages.add(String.format(COST_WON_MESSAGE, nbRelevantTests * attackerCostActivity));
            messages.add(MUTANT_ALIVE_1_MESSAGE);
        } else {
            int resultDefender = attackerStartCostActivity + attackerCostActivity * nbRelevantTests;
            boolean updateAttackerCost2 = MultiplayerGameDAO.updateAttackerCostAbstract(game, resultDefender);
            messages.add(String.format(COST_WON_MESSAGE, nbRelevantTests * attackerCostActivity));
            messages.add(String.format(MUTANT_ALIVE_N_MESSAGE, nbRelevantTests));
        }
        Event notif = new Event(-1, game.getId(), u.getId(), u.getUsername() + "&#39;s mutant survives the test suite.",
                EventType.ATTACKER_MUTANT_SURVIVED, EventStatus.GAME, new Timestamp(System.currentTimeMillis()));
        eventDAO.insert(notif);
    }
}
