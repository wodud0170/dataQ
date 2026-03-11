package com.ndata.quality.tool;

import java.sql.SQLException;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.hibernate.query.NativeQuery;
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

    public DBHandler getDBHandler(DataSourceVo dataSource) throws ClassNotFoundException, SQLException {
		dataSource.setPwd(securityUtils.decryptStr(dataSource.getPwd()));
		return DBHandler.getDBHandler(dataSource);
	}

	public String getQueryString(String queryId) {
		System.out.println("entityManager : " + entityManager);
		Query query = entityManager.createNamedQuery(queryId);
		System.out.println("entityManager.createNamedQuery(queryId) : " + query);
		return query.unwrap(NativeQuery.class).getQueryString();
	}

}
