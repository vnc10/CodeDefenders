package org.codedefenders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class User {

	private static final Logger logger = LoggerFactory.getLogger(User.class);

	public int id;
	public String username;
	public String password;
	public String email;

	public User(String username, String password) {
		this.username = username;
		this.password = password;
	}

	public User(String username, String password, String email) {
		this(username, password);
		this.email = email;
	}

	public User(int id, String username, String password, String email) {
		this(username, password, email);
		this.id = id;
	}

	public boolean insert() {

		Connection conn = null;
		Statement stmt = null;
		String sql = null;

		try {
			conn = DatabaseAccess.getConnection();

			stmt = conn.createStatement();
			logger.debug("Calling BCryptPasswordEncoder.encode");
			BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
			String safePassword = passwordEncoder.encode(password);

			if (id <= 0)
				sql = String.format("INSERT INTO users (Username, Password, Email) VALUES ('%s', '%s', '%s');", username, safePassword, email);
			else
				sql = String.format("INSERT INTO users (User_ID, Username, Password, Email) VALUES (%d, '%s', '%s', '%s');", id, username, safePassword, email);

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
		} // Handle errors for JDBC
		catch (Exception e) {
			System.out.println(e);
		} // Handle errors for Class.forName
		finally {
			try {
				if (stmt != null) {
					stmt.close();
				}
			} catch (SQLException se2) {
			} // Nothing we can do
			try {
				if (conn != null) {
					conn.close();
				}
			} catch (SQLException se) {
				System.out.println(se);
			}
		}
		return false;
	}
}