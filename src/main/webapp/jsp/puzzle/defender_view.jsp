<%--

    Copyright (C) 2016-2019 Code Defenders contributors

    This file is part of Code Defenders.

    Code Defenders is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or (at
    your option) any later version.

    Code Defenders is distributed in the hope that it will be useful, but
    WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
    General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Code Defenders. If not, see <http://www.gnu.org/licenses/>.

--%>
<%@ page import="org.codedefenders.game.GameState" %>
<%@ page import="org.codedefenders.game.puzzle.PuzzleGame" %>
<%@ page import="static org.codedefenders.util.Constants.*" %>
<%@ page import="org.codedefenders.game.puzzle.Puzzle" %>
<%@ page import="org.codedefenders.game.GameClass" %>
<%@ page import="org.codedefenders.util.Paths" %>
<%@ page import="org.codedefenders.validation.code.CodeValidatorLevel" %>
<%@ page import="org.codedefenders.game.GameMode" %>
<%@ page import="org.codedefenders.game.Mutant" %>
<%@ page import="java.util.LinkedList" %>
<%@ page import="org.codedefenders.util.Constants" %>

<%--
    Puzzle game view for a defender. Retrieves the given puzzle game
    from the request and calls the required game components.

    @param PuzzleGame Constants#REQUEST_ATTRIBUTE_PUZZLE_GAME
        The puzzle game to be displayed.
--%>

<jsp:include page="/jsp/header_main.jsp"/>

</div></div></div></div></div>

<%-- Set request attributes for the components. --%>
<%
    PuzzleGame game = (PuzzleGame) request.getAttribute(REQUEST_ATTRIBUTE_PUZZLE_GAME);

    final GameClass cut = game.getCUT();
    final Puzzle puzzle = game.getPuzzle();
%>

<jsp:useBean id="classViewer" class="org.codedefenders.beans.game.ClassViewerBean" scope="request"/>
<% classViewer.setClassCode(game.getCUT()); %>
<% classViewer.setDependenciesForClass(game.getCUT()); %>

<jsp:useBean id="testEditor" class="org.codedefenders.beans.game.TestEditorBean" scope="request"/>
<% testEditor.setEditableLinesForPuzzle(puzzle); %>
<% testEditor.setMockingEnabled(false); %>

<jsp:useBean id="testAccordion" class="org.codedefenders.beans.game.TestAccordionBean" scope="request"/>
<% testAccordion.setTestAccordionData(cut, game.getTests(), game.getMutants()); %>

<%
    /* test_editor */
    String previousTestCode = (String) request.getSession().getAttribute(Constants.SESSION_ATTRIBUTE_PREVIOUS_TEST);
    request.getSession().removeAttribute(Constants.SESSION_ATTRIBUTE_PREVIOUS_TEST);
    if (previousTestCode != null) {
        testEditor.setPreviousTestCode(previousTestCode);
    } else {
        testEditor.setTestCodeForClass(cut);
    }

    /* mutants_list */
    request.setAttribute("mutantsAlive", game.getAliveMutants());
    request.setAttribute("mutantsKilled", game.getKilledMutants());
    request.setAttribute("mutantsEquivalent", new LinkedList<Mutant>());
    request.setAttribute("mutantsMarkedEquivalent", game.getMutantsMarkedEquivalentPending());
    request.setAttribute("markEquivalent", false);
    request.setAttribute("viewDiff", true);
    request.setAttribute("gameType", GameMode.PUZZLE);
    request.setAttribute("gameId", game.getId());

    /* game_highlighting */
    request.setAttribute("codeDivSelector", "#cut-div");
    request.setAttribute("tests", game.getTests());
    request.setAttribute("mutants", game.getMutants());
    request.setAttribute("showEquivalenceButton", true);
    // request.setAttribute("gameType", GameMode.PUZZLE);
    // request.setAttribute("gameId", game.getId());

    /* mutant_explanation */
    request.setAttribute("mutantValidatorLevel", CodeValidatorLevel.MODERATE);

    /* test_progressbar */
    // request.setAttribute("gameId", game.getId());

    final String title = puzzle.getTitle();
    final String description = puzzle.getDescription();
%>

<jsp:include page="/jsp/push_notifications.jsp"/>

<div class="game-container">
    <div class="row" style="padding: 0px 15px;">
        <h4 class="col-md-2"><b><%=title%></b></h4>
        <h4><%=description%></h4>
    </div>
    <hr class="hr-primary" style="margin: 5px">
    <div class="row" style="padding: 0px 15px;">
        <div class="col-md-6" id="cut-div">
            <h3>Class Under Test</h3>
            <jsp:include page="/jsp/game_components/class_viewer.jsp"/>
            <jsp:include page="/jsp/game_components/game_highlighting.jsp"/>
            <jsp:include page="/jsp/game_components/mutant_explanation.jsp"/>
        </div>

        <div class="col-md-6" id="ut-div">
            <jsp:include page="/jsp/game_components/push_test_progress_bar.jsp"/>
            <h3>Write a new JUnit test here
                <button type="submit" class="btn btn-primary btn-game btn-right" id="submitTest" form="def"
                        onClick="testProgressBar(); this.form.submit(); this.disabled=true; this.value='Defending...';"
                        <% if (game.getState() != GameState.ACTIVE) { %> disabled <% } %>>
                    Defend!
                </button>
            </h3>
            <form id="def"
                  action="<%=request.getContextPath() + Paths.PUZZLE_GAME%>"
                  method="post">
                <input type="hidden" name="formType" value="createTest">
                <input type="hidden" name="gameId" value="<%= game.getId() %>">

                <jsp:include page="/jsp/game_components/test_editor.jsp"/>
            </form>
            <jsp:include page="/jsp/game_components/editor_help_config_toolbar.jsp"/>
        </div>
    </div>

    <div class="row" style="padding: 0px 15px;">
        <div class="col-md-6" id="mutants-div">
            <h3>Existing Mutants</h3>
            <jsp:include page="/jsp/game_components/mutants_list.jsp"/>
        </div>

        <div class="col-md-6">
            <h3>JUnit tests</h3>
            <jsp:include page="/jsp/game_components/test_accordion.jsp"/>
        </div>
    </div>
</div>

<jsp:include page="/jsp/game_components/editor_help_config_modal.jsp"/>

<%@ include file="/jsp/footer_game.jsp"%>
