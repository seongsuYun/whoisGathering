package com.uwiseone.swp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EmailExternalCrawlingController {
	
	public static Connection getConnection() {
		Connection conn = null;

		try {
			String url = "jdbc:oracle:thin:@10.2.31.107:1521:wodb1";
			String id = "sradmin";
			String pw = "sradmin";

			Class.forName("oracle.jdbc.driver.OracleDriver");
			conn = DriverManager.getConnection(url,id,pw);
			System.out.println("Database 연결되었습니다.");
		} catch(Exception e) {
			System.out.println("Database 연결 중 오류가 발생하였습니다.");
			e.printStackTrace();
		}
		
		return conn;
	}
	
	private List<CorpDetailEntity> getCorpList() {
		Connection conn = null;
		ResultSet rs = null;
		PreparedStatement pstmt = null;
		
		List<CorpDetailEntity> list = null;
		
		StringBuilder query = null;
		try {
			conn = getConnection();
			query = new StringBuilder();
			query.append(" SELECT TRIM(DOMAINID) AS CORP_PK, TRIM(HOMEPAGE) AS HOMEPAGE FROM SRADMIN.T_CORP_EXTERNAL ");

			pstmt = conn.prepareStatement(query.toString());
			rs = pstmt.executeQuery();
			
			list = new ArrayList<CorpDetailEntity>();
			CorpDetailEntity entity = null;
			while(rs.next()) {
				entity = new CorpDetailEntity();
				entity.setCorpPk(rs.getString("CORP_PK"));
				entity.setHomepage(rs.getString("HOMEPAGE"));
				list.add(entity);
			}
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			if(rs != null) try{rs.close();}catch(SQLException sqle){}
			if(pstmt != null) try{pstmt.close();}catch(SQLException sqle){}
			if(conn != null) try{conn.close();}catch(SQLException sqle){}
		}
		
		return list;
	}
	
	private String gatheringEmail(CorpDetailEntity entity) throws Exception {

		String command = "whois " + entity.getHomepage();
		BufferedReader br;
		StringBuffer buffer = new StringBuffer();
		try {
			Process process = Runtime.getRuntime().exec(command);
			
			br = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
			
			String line = null;
			while((line = br.readLine()) != null) {
				buffer.append(line).append("\n");
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		return buffer.toString();
	}
	
	private void saveGatheringResult(CorpDetailEntity entity, String rawString) throws Exception {
		Connection conn = null;
		PreparedStatement pstmt = null;
		
		StringBuilder query = new StringBuilder();
		query.append(" UPDATE SRADMIN.T_CORP_EXTERNAL SET RAW_STR = ? WHERE DOMAINID = ? ");
		try {
			conn = getConnection();
			conn.setAutoCommit(false);
			pstmt = conn.prepareStatement(query.toString());
			pstmt.setString(1, rawString);
			pstmt.setString(2, entity.getCorpPk());
			int resultCnt = pstmt.executeUpdate();
			System.out.println("==========>> RAW데이터 저장("+resultCnt+") 종료");
			
			conn.commit();			
		} catch(Exception e) {
			conn.rollback();
			e.printStackTrace();
		} finally {
			if(pstmt != null) try{pstmt.close();}catch(SQLException sqle){}
			if(conn != null) try{conn.close();}catch(SQLException sqle){}    
		}
	}
	
	@GetMapping("/externalProcess")
	public void process() {
		EmailExternalCrawlingController emailCrawling = new EmailExternalCrawlingController();
		
		// 수집대상 기업 리스트 조회
		List<CorpDetailEntity> corpList = emailCrawling.getCorpList();
		
		int successCnt = 0;
		String rawString = "";

		// whois 응답결과 수집
		try {
			for(CorpDetailEntity entity : corpList) {
				rawString = emailCrawling.gatheringEmail(entity);
				
				System.out.println("rawString : " + rawString);
				
				Thread.sleep(100);
				
				if(rawString != null && !"".equals(rawString)) {
					emailCrawling.saveGatheringResult(entity, rawString);
					++successCnt;
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("수집 완료(성공건수 : "+successCnt+")");
	}
}
