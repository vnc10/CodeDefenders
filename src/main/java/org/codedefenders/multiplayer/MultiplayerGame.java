package org.codedefenders.multiplayer;

import org.codedefenders.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import static org.codedefenders.Mutant.Equivalence.PENDING_TEST;

public class MultiplayerGame {

	private static final Logger logger = LoggerFactory.getLogger(MultiplayerGame.class);

	private int id;

	private int classId;


	private int creatorId;
	private int defenderValue;
	private int attackerValue;
	private float lineCoverage;
	private float mutantCoverage;
	private float price;

	private Game.Level level;

	public void setId(int id) {
		this.id = id;
	}

	public void setClassId(int classId) {
		this.classId = classId;
	}

	public int getCreatorId() {
		return creatorId;
	}

	public void setCreatorId(int creatorId) {
		this.creatorId = creatorId;
	}

	public int getDefenderValue() {
		return defenderValue;
	}

	public void setDefenderValue(int defenderValue) {
		this.defenderValue = defenderValue;
	}

	public int getAttackerValue() {
		return attackerValue;
	}

	public void setAttackerValue(int attackerValue) {
		this.attackerValue = attackerValue;
	}

	public float getLineCoverage() {
		return lineCoverage;
	}

	public void setLineCoverage(float lineCoverage) {
		this.lineCoverage = lineCoverage;
	}

	public float getMutantCoverage() {
		return mutantCoverage;
	}

	public void setMutantCoverage(float mutantCoverage) {
		this.mutantCoverage = mutantCoverage;
	}

	public float getPrice() {
		return price;
	}

	public void setPrice(float price) {
		this.price = price;
	}

	public MultiplayerGame(int classId, int creatorId, Game.Level level, float lineCoverage, float mutantCoverage, float price) {
		this.classId = classId;
		this.creatorId = creatorId;
		this.level = level;
		this.lineCoverage = lineCoverage;
		this.mutantCoverage = mutantCoverage;
		this.price = price;
	}

	public int getId() {
		return id;
	}

	public int getClassId() {
		return classId;
	}

	public String getClassName() {
		return DatabaseAccess.getClassForKey("Class_ID", classId).name;
	}

	public GameClass getCUT() {
		return DatabaseAccess.getClassForKey("Class_ID", classId);
	}

	public Game.Level getLevel() {
		return this.level;
	}

	public void setLevel(Game.Level level) {
		this.level = level;
	}


	public ArrayList<MultiplayerMutant> getMutants() {
		int[] attackers = getAttackerIds();
		return DatabaseAccess.getMutantsForAttackers(attackers);
	}

	public ArrayList<MultiplayerMutant> getAliveMutants() {
		ArrayList<MultiplayerMutant> aliveMutants = new ArrayList<>();
		for (MultiplayerMutant m : getMutants()) {
			if (m.isAlive() && (m.getClassFile() != null)) {
				aliveMutants.add(m);
			}
		}
		return aliveMutants;
	}

	public ArrayList<MultiplayerMutant> getKilledMutants() {
		ArrayList<MultiplayerMutant> killedMutants = new ArrayList<>();
		for (MultiplayerMutant m : getMutants()) {
			if (!m.isAlive() && (m.getClassFile() != null)) {
				killedMutants.add(m);
			}
		}
		return killedMutants;
	}

	public ArrayList<MultiplayerMutant> getMutantsMarkedEquivalent() {
		ArrayList<MultiplayerMutant> equivMutants = new ArrayList<>();
		for (MultiplayerMutant m : getMutants()) {
			if (m.isAlive() && m.getEquivalent().equals(PENDING_TEST)) {
				equivMutants.add(m);
			}
		}
		return equivMutants;
	}

	public MultiplayerMutant getMutantByID(int mutantID) {
		for (MultiplayerMutant m : getMutants()) {
			if (m.getId() == mutantID)
				return m;
		}
		return null;
	}

	public ArrayList<Test> getTests() {
		return DatabaseAccess.getTestsForGame(id);
	}

	public ArrayList<Test> getExecutableTests() {
		ArrayList<Test> allTests = new ArrayList<>();
		int[] defenders = getDefenderIds();
		for (int i = 0; i < defenders.length; i++){
			ArrayList<Test> tests = DatabaseAccess.getExecutableTestsForMultiplayerGame(defenders[i]);
			allTests.addAll(tests);
		}
		return allTests;
	}

	public int[] getDefenderIds(){
		return DatabaseAccess.getDefendersForMultiplayerGame(getId());
	}

	public int[] getAttackerIds(){
		return DatabaseAccess.getAttackersForMultiplayerGame(getId());
	}

	public boolean addUserAsAttacker(int userId) {
		String sql = String.format("INSERT INTO attackers " +
						"(Game_ID, User_ID, Points) VALUES " +
						"(%d, %d, 0);",
				id, userId);

		return runStatement(sql);
	}

	public boolean addUserAsDefender(int userId) {
		String sql = String.format("INSERT INTO defenders " +
						"(Game_ID, User_ID, Points) VALUES " +
						"(%d, %d, 0);",
				id, userId);

		return runStatement(sql);
	}

	public boolean runStatement(String sql) {

		Connection conn = null;
		Statement stmt = null;

		System.out.println(sql);

		// Attempt to insert game info into database
		try {
			conn = DatabaseAccess.getConnection();

			stmt = conn.createStatement();
			stmt.execute(sql, Statement.RETURN_GENERATED_KEYS);

			ResultSet rs = stmt.getGeneratedKeys();

			if (rs.next()) {
				id = rs.getInt(1);
				stmt.close();
				conn.close();
				return true;
			}

		} catch (SQLException se) {
			System.out.println(se);
			//Handle errors for JDBC
		} catch (Exception e) {
			System.out.println(e);
			//Handle errors for Class.forName
		} finally {
			//finally block used to close resources
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException se2) {
			}// nothing we can do

			try {
				if (conn != null)
					conn.close();
			} catch (SQLException se) {
				System.out.println(se);
			}//end finally try
		} //end try

		return false;
	}

	public boolean insert() {

		Connection conn = null;
		Statement stmt = null;
		String sql = null;

		// Attempt to insert game info into database
		try {
			conn = DatabaseAccess.getConnection();

			stmt = conn.createStatement();
			sql = String.format("INSERT INTO multiplayer_games " +
					"(Class_ID, Level, Price, Defender_Value, Attacker_Value, Coverage_Goal, Mutant_Goal, Creator_ID) VALUES " +
					"('%s', 	'%s', '%f', 	'%d',			'%d',			'%f',			'%f',		'%d');",
					classId, level.name(), price, defenderValue, attackerValue, lineCoverage, mutantCoverage, creatorId);

			stmt.execute(sql, Statement.RETURN_GENERATED_KEYS);

			ResultSet rs = stmt.getGeneratedKeys();

			if (rs.next()) {
				id = rs.getInt(1);
				stmt.close();
				conn.close();
				return true;
			}

		} catch (SQLException se) {
			System.out.println(se);
			//Handle errors for JDBC
		} catch (Exception e) {
			System.out.println(e);
			//Handle errors for Class.forName
		} finally {
			//finally block used to close resources
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException se2) {
			}// nothing we can do

			try {
				if (conn != null)
					conn.close();
			} catch (SQLException se) {
				System.out.println(se);
			}//end finally try
		} //end try

		return false;
	}

	public boolean update() {

		Connection conn = null;
		Statement stmt = null;
		String sql = null;

		try {
			conn = DatabaseAccess.getConnection();

			// Get all rows from the database which have the chosen username
			stmt = conn.createStatement();
				sql = String.format("UPDATE multiplayer_games SET " +
						"Class_ID = '%s', Level = '%s', Price = %f, Defender_Value=%d, Attacker_Value=%d, Coverage_Goal=%f" +
						", Mutant_Goal=%f WHERE ID='%d'",
						classId, level.name(), price, defenderValue, attackerValue, lineCoverage, mutantCoverage, id);
			stmt.execute(sql);
			return true;

		} catch (SQLException se) {
			System.out.println(se);
			//Handle errors for JDBC
			se.printStackTrace();
		} catch (Exception e) {
			System.out.println(e);
			//Handle errors for Class.forName
			e.printStackTrace();
		} finally {
			//finally block used to close resources
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException se2) {
			}// nothing we can do

			try {
				if (conn != null)
					conn.close();
			} catch (SQLException se) {
				se.printStackTrace();
			}//end finally try
		} //end try

		return false;
	}

	public Participance getParticipance(int userId){
		return DatabaseAccess.getParticipance(userId, getId());
	}
}