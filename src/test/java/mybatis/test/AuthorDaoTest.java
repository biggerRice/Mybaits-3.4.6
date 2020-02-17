package mybatis.test;


import com.dm.dao.AuthorDao;
import com.dm.dao.UserDao;
import com.dm.entity.AuthorEntity;
import com.dm.entity.User;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class AuthorDaoTest {
    private static SqlSessionFactory sessionFactory = null;

    @Test
    public void findAuthorMapById() {
        SqlSession sqlSession = sessionFactory.openSession();
        AuthorDao authorMapper = sqlSession.getMapper(AuthorDao.class);
        Map authorMap = authorMapper.findAuthorMap(4);
        System.out.println("end-----------");
    }

    @Test
    public void getAuthorById() {
        SqlSession sqlSession = sessionFactory.openSession();
        AuthorDao authorMapper = sqlSession.getMapper(AuthorDao.class);
        AuthorEntity authorEntity = authorMapper.getAuthorById(4);
        System.out.println("end-----------");
    }

    @Test
    public void addAuthor() {
        try {
            SqlSession sqlSession = sessionFactory.openSession();
            AuthorDao authorMapper = sqlSession.getMapper(AuthorDao.class);
            AuthorEntity authorEntity = new AuthorEntity();
            authorEntity.setName("wind");
            authorEntity.setAge(19);
            int addFlag = authorMapper.addAuthor(authorEntity);
            // 提交事务，数据存入数据库
            sqlSession.commit();
            System.out.println("end-----------addFlag:"+addFlag);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @BeforeClass
    public static void getSessionFactory() {
        String resource = "mybatis-config.xml";
        try {  
            sessionFactory = new SqlSessionFactoryBuilder().build(Resources
                    .getResourceAsReader(resource));
        } catch (IOException e) {
            e.printStackTrace();  
        }  
    }
    
}