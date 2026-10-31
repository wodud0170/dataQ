package com.ndata.quality.tool;

import java.sql.SQLException;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.hibernate.query.NativeQuery;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.ndata.bean.SecurityManager;
import com.ndata.datasource.dbms.handler.DBHandler;
import com.ndata.model.DataSourceVo;

@Repository
public class DataSourceUtils {

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private SecurityManager securityUtils;

	/**
	 * 접속 핸들러. 넘겨받은 VO 는 건드리지 않는다 (110 11.7-26) — 복사본에 복호화한 비밀번호와
	 * Service Name 드라이버명을 넣어 접속하므로, 같은 VO 로 다시 불러도 암호문 그대로라 다시 접속된다.
	 */
    public DBHandler getDBHandler(DataSourceVo dataSource) throws ClassNotFoundException, SQLException {
		DataSourceVo conn = new DataSourceVo();
		BeanUtils.copyProperties(dataSource, conn);
		conn.setPwd(securityUtils.decryptStr(dataSource.getPwd()));
		// Oracle/Tibero Service Name: drivers.xml 의 "(Service Name)" 드라이버로 매칭
		if ("Service Name".equals(conn.getConnProps())) {
			String dbmsTp = conn.getDbmsTp();
			if ("Oracle".equals(dbmsTp) || "Tibero".equals(dbmsTp)) {
				conn.setDriverName(dbmsTp + "(Service Name)");
			}
		}
		return DBHandler.getDBHandler(conn);
	}

	public String getQueryString(String queryId) {
		System.out.println("entityManager : " + entityManager);
		Query query = entityManager.createNamedQuery(queryId);
		System.out.println("entityManager.createNamedQuery(queryId) : " + query);
		return query.unwrap(NativeQuery.class).getQueryString();
	}

}
