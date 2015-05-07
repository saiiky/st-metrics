package com.smartertravel.metrics.aop;

import com.smartertravel.metrics.aop.TimingAspect.DefaultKeyGenerator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.boot.actuate.metrics.GaugeService;

import java.lang.reflect.Method;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyDouble;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class TimingAspectTest {

    interface UserDao {
        @SuppressWarnings("unused")
        boolean userExists(String id);
    }

    static class UserDaoMysql implements UserDao {

        @Timed("mysqlDao.userExists")
        @Override
        public boolean userExists(String id) {
            return false;
        }
    }

    static class UserDaoHystrix implements UserDao {

        @Timed
        @Override
        public boolean userExists(String id) {
            return false;
        }
    }

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Signature signature;

    @Mock
    private UserDao userDao;

    @Mock
    private GaugeService gaugeService;

    @Test
    public void testDefaultKeyGeneratorGetKeyAnnotationValue() throws NoSuchMethodException {
        final UserDaoMysql dao = new UserDaoMysql();
        final Method method = dao.getClass().getMethod("userExists", String.class);
        final Timed[] annotations = method.getAnnotationsByType(Timed.class);

        final DefaultKeyGenerator keyGenerator = new DefaultKeyGenerator();
        assertEquals("mysqlDao.userExists", keyGenerator.getKey(joinPoint, dao, annotations[0]));
    }

    @Test
    public void testDefaultKeyGeneratorGetKeyDerivedValue() throws NoSuchMethodException {
        final UserDaoHystrix dao = new UserDaoHystrix();
        final Method method = dao.getClass().getMethod("userExists", String.class);
        final Timed[] annotations = method.getAnnotationsByType(Timed.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("userExists");

        final DefaultKeyGenerator keyGenerator = new DefaultKeyGenerator();
        assertEquals("UserDaoHystrix.userExists", keyGenerator.getKey(joinPoint, dao, annotations[0]));
    }

    @Test
    public void testPerformanceLog() throws Throwable {
        final UserDaoHystrix dao = new UserDaoHystrix();
        final Method method = dao.getClass().getMethod("userExists", String.class);
        final Timed[] annotations = method.getAnnotationsByType(Timed.class);

        when(joinPoint.proceed()).thenReturn(true);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("userExists");

        final TimingAspect aspect = new TimingAspect(gaugeService);
        final Object result = aspect.performanceLog(joinPoint, dao, annotations[0]);

        assertNotNull("Expected non-null result type", result);
        assertTrue("Expected boolean return type from aspect, was " + result.getClass().getName(), result instanceof Boolean);
        verify(gaugeService).submit(eq("timer.UserDaoHystrix.userExists"), anyDouble());
    }
}