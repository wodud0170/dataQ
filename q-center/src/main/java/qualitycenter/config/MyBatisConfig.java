package qualitycenter.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.TypeHandler;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.ndata.common.handler.NDJobStatusHandler;
import com.ndata.common.handler.NDObjectTypeHandler;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;


@Configuration
@EnableTransactionManagement
/*@RefreshScope*/
//@MapperScan(basePackages = "manager")
/*@DependsOn(value = {"getDatasource"})*/
public class MyBatisConfig {
	
	@Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariConfig hikariConfig() {
        return new HikariConfig();
    }
	
	@Bean
    public DataSource dataSource() {
        return new HikariDataSource(hikariConfig());
    }
	
    @Bean
    /*@RefreshScope*/
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        final SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        sessionFactory.setMapperLocations(resolver.getResources("classpath*:mapper/**/*.xml"));
        // Enum 타입 처리를 위한 TypeHandler 를 등록한다.
        sessionFactory.setTypeHandlers(new TypeHandler[] {
                //new BooleanTypeHandler(),
                new NDObjectTypeHandler(),
                new NDJobStatusHandler()
                //new EnumOrdinalTypeHandler<NDObjectType>(NDObjectType.class)
            });
        return sessionFactory.getObject();
    }

    @Bean
    /*@RefreshScope*/
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) throws Exception {
        final SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
        return sqlSessionTemplate;
    }
    
    @Bean
	public PlatformTransactionManager txManager() throws Exception {
		return new DataSourceTransactionManager(dataSource());
	}
}
