package com.inmobi.grill.driver.cube;

import static org.mockito.Matchers.any;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.ql.Context;
import org.apache.hadoop.hive.ql.cube.parse.CubeQueryContext;
import org.apache.hadoop.hive.ql.cube.parse.CubeQueryRewriter;
import org.apache.hadoop.hive.ql.cube.parse.HQLParser;
import org.apache.hadoop.hive.ql.parse.ASTNode;
import org.apache.hadoop.hive.ql.parse.HiveParser;
import org.apache.hadoop.hive.ql.parse.ParseException;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.inmobi.grill.api.GrillDriver;
import com.inmobi.grill.api.GrillResultSet;
import com.inmobi.grill.api.QueryHandle;
import com.inmobi.grill.api.QueryPlan;
import com.inmobi.grill.api.QueryStatus;
import com.inmobi.grill.driver.cube.CubeGrillDriver.CubeQueryInfo;
import com.inmobi.grill.exception.GrillException;

public class TestCubeDriver {

  Configuration conf = new Configuration();
  CubeGrillDriver cubeDriver;

  @BeforeTest
  public void beforeTest() throws Exception {
    //conf.set(CubeGrillDriver.ENGINE_DRIVER_CLASSES,
    //    MockDriver.class.getCanonicalName());
    cubeDriver = new CubeGrillDriver(conf);
    conf.setInt("mock.driver.test.val", 5);
  }

  @AfterTest
  public void afterTest() throws Exception {
  }

  @Test
  public void testCubeDriver() throws Exception {
    String addQ = "add jar xyz.jar";
    GrillResultSet result = cubeDriver.execute(addQ, conf);
    Assert.assertNotNull(result);
    Assert.assertEquals(((MockDriver)cubeDriver.getDrivers().get(0)).query, addQ);

    String setQ = "set xyz=random";
    result = cubeDriver.execute(setQ, conf);
    Assert.assertNotNull(result);
    Assert.assertEquals(((MockDriver)cubeDriver.getDrivers().get(0)).query, setQ);

    String query = "select name from table";
    QueryPlan plan = cubeDriver.explain(query, conf);
    String planString = plan.getPlan();
    Assert.assertEquals(query, planString);

    // execute async from handle
    cubeDriver.executePrepareAsync(plan.getHandle(), conf);
    Assert.assertEquals(cubeDriver.getStatus(plan.getHandle()).getStatus(),
        QueryStatus.Status.SUCCESSFUL); 
    Assert.assertFalse(cubeDriver.cancelQuery(plan.getHandle()));

    // execute sync from handle
    result = cubeDriver.executePrepare(plan.getHandle(), conf);
    Assert.assertNotNull(result);
    Assert.assertNotNull(result.getMetadata());
    Assert.assertEquals(cubeDriver.getStatus(plan.getHandle()).getStatus(),
        QueryStatus.Status.SUCCESSFUL); 

    cubeDriver.closeQuery(plan.getHandle());

    // getStatus on closed query
    Throwable th = null;
    try {
      cubeDriver.getStatus(plan.getHandle());
    } catch (GrillException e) {
      th = e;
    }
    Assert.assertNotNull(th);

    result = cubeDriver.execute(query, conf);
    Assert.assertNotNull(result);
    Assert.assertNotNull(result.getMetadata());

    QueryHandle handle = cubeDriver.executeAsync(query, conf);
    Assert.assertEquals(cubeDriver.getStatus(handle).getStatus(),
        QueryStatus.Status.SUCCESSFUL); 
    Assert.assertFalse(cubeDriver.cancelQuery(handle));

    cubeDriver.closeQuery(handle);

    th = null;
    try {
      cubeDriver.getStatus(handle);
    } catch (GrillException e) {
      th = e;
    }
    Assert.assertNotNull(th);
  }

  private CubeQueryRewriter getMockedRewriter()
      throws SemanticException, ParseException {
    CubeQueryRewriter mockwriter = Mockito.mock(CubeQueryRewriter.class);
    Mockito.when(mockwriter.rewrite(any(String.class))).thenAnswer(
        new Answer<CubeQueryContext>() {
          @Override
          public CubeQueryContext answer(InvocationOnMock invocation)
              throws Throwable {
            Object[] args = invocation.getArguments();
            return getMockedCubeContext((String)args[0]);
          }
        });
    Mockito.when(mockwriter.rewrite(any(ASTNode.class))).thenAnswer(
        new Answer<CubeQueryContext>() {
          @Override
          public CubeQueryContext answer(InvocationOnMock invocation)
              throws Throwable {
            Object[] args = invocation.getArguments();
            return getMockedCubeContext((ASTNode)args[0]);
          }
        });
    return mockwriter;
  }

  private CubeQueryContext getMockedCubeContext(String query)
      throws SemanticException, ParseException {
    CubeQueryContext context = Mockito.mock(CubeQueryContext.class);
    Mockito.when(context.toHQL()).thenReturn(query.substring(4));
    Mockito.when(context.toAST(any(Context.class))).thenReturn(
        HQLParser.parseHQL(query.substring(4)));
    return context;
  }

  private CubeQueryContext getMockedCubeContext(ASTNode ast)
      throws SemanticException, ParseException {
    CubeQueryContext context = Mockito.mock(CubeQueryContext.class);
    if (ast.getToken().getType() == HiveParser.TOK_QUERY) {
      if (((ASTNode) ast.getChild(0)).getToken().getType() == HiveParser.KW_CUBE) {
        // remove cube child from AST
        for (int i = 0; i < ast.getChildCount() - 1; i++) {
          ast.setChild(i, ast.getChild(i + 1));
        }
        ast.deleteChild(ast.getChildCount() - 1);
      }
    }
    StringBuilder builder = new StringBuilder();
    HQLParser.toInfixString(ast, builder);
    Mockito.when(context.toHQL()).thenReturn(builder.toString());
    Mockito.when(context.toAST(any(Context.class))).thenReturn(ast);
    return context;
  }

  @Test
  public void testCubeQuery()
      throws Exception {
    CubeGrillDriver mockedDriver = Mockito.spy(cubeDriver);
    CubeQueryRewriter mockWriter = getMockedRewriter();
    Mockito.doReturn(mockWriter).when(mockedDriver).getRewriter(
        any(GrillDriver.class));
    String q1 = "select name from table";
    Assert.assertFalse(cubeDriver.isCubeQuery(q1));
    List<CubeQueryInfo> cubeQueries = mockedDriver.findCubePositions(q1);
    Assert.assertEquals(cubeQueries.size(), 0);
    mockedDriver.rewriteQuery(q1);

    String q2 = "cube select name from table";
    Assert.assertTrue(cubeDriver.isCubeQuery(q2));
    cubeQueries = mockedDriver.findCubePositions(q2);
    Assert.assertEquals(cubeQueries.size(), 1);
    Assert.assertEquals(cubeQueries.get(0).query, "cube select name from table");
    mockedDriver.rewriteQuery(q2);

    q2 = "explain cube select name from table";
    Assert.assertTrue(cubeDriver.isCubeQuery(q2));
    cubeQueries = mockedDriver.findCubePositions(q2);
    Assert.assertEquals(cubeQueries.size(), 1);
    Assert.assertEquals(cubeQueries.get(0).query, "cube select name from table");
    mockedDriver.rewriteQuery(q2);

    q2 = "select * from (cube select name from table) a";
    Assert.assertTrue(cubeDriver.isCubeQuery(q2));
    cubeQueries = mockedDriver.findCubePositions(q2);
    Assert.assertEquals(cubeQueries.size(), 1);
    Assert.assertEquals(cubeQueries.get(0).query, "cube select name from table");
    mockedDriver.rewriteQuery(q2);

    q2 = "select * from (cube select name from table)a";
    Assert.assertTrue(cubeDriver.isCubeQuery(q2));
    cubeQueries = mockedDriver.findCubePositions(q2);
    Assert.assertEquals(cubeQueries.size(), 1);
    Assert.assertEquals(cubeQueries.get(0).query, "cube select name from table");
    mockedDriver.rewriteQuery(q2);

    q2 = "select * from  (  cube select name from table   )     a";
    Assert.assertTrue(cubeDriver.isCubeQuery(q2));
    cubeQueries = mockedDriver.findCubePositions(q2);
    Assert.assertEquals(cubeQueries.size(), 1);
    Assert.assertEquals(cubeQueries.get(0).query, "cube select name from table");
    mockedDriver.rewriteQuery(q2);

    q2 = "select * from (      cube select name from table where" +
        " (name = 'ABC'||name = 'XYZ')&&(key=100)   )       a";
    Assert.assertTrue(cubeDriver.isCubeQuery(q2));
    cubeQueries = mockedDriver.findCubePositions(mockedDriver.getReplacedQuery(q2));
    Assert.assertEquals(cubeQueries.size(), 1);
    Assert.assertEquals(cubeQueries.get(0).query, "cube select name from" +
        " table where (name = 'ABC' OR name = 'XYZ') AND (key=100)");
    mockedDriver.rewriteQuery(q2);

    q2 = "select * from (cube select name from table) a join (cube select" +
        " name2 from table2) b";
    Assert.assertTrue(cubeDriver.isCubeQuery(q2));
    cubeQueries = mockedDriver.findCubePositions(q2);
    Assert.assertEquals(cubeQueries.size(), 2);
    Assert.assertEquals(cubeQueries.get(0).query, "cube select name from table");
    Assert.assertEquals(cubeQueries.get(1).query, "cube select name2 from table2");
    mockedDriver.rewriteQuery(q2);

    q2 = "select * from (cube select name from table) a full outer join" +
        " (cube select name2 from table2) b on a.name=b.name2";
    Assert.assertTrue(cubeDriver.isCubeQuery(q2));
    cubeQueries = mockedDriver.findCubePositions(q2);
    Assert.assertEquals(cubeQueries.size(), 2);
    Assert.assertEquals(cubeQueries.get(0).query, "cube select name from table");
    Assert.assertEquals(cubeQueries.get(1).query, "cube select name2 from table2");
    mockedDriver.rewriteQuery(q2);

    q2 = "select * from (cube select name from table) a join (select name2 from table2) b";
    Assert.assertTrue(cubeDriver.isCubeQuery(q2));
    cubeQueries = mockedDriver.findCubePositions(q2);
    Assert.assertEquals(cubeQueries.size(), 1);
    Assert.assertEquals(cubeQueries.get(0).query, "cube select name from table");
    mockedDriver.rewriteQuery(q2);

    q2 = "select * from (cube select name from table union all cube select name2 from table2) u";
    Assert.assertTrue(cubeDriver.isCubeQuery(q2));
    cubeQueries = mockedDriver.findCubePositions(q2);
    mockedDriver.rewriteQuery(q2);
    Assert.assertEquals(cubeQueries.size(), 2);
    Assert.assertEquals(cubeQueries.get(0).query, "cube select name from table");
    Assert.assertEquals(cubeQueries.get(1).query, "cube select name2 from table2");
    mockedDriver.rewriteQuery(q2);

    q2 = "select u.* from (select name from table    union all       cube select name2 from table2)   u";
    Assert.assertTrue(cubeDriver.isCubeQuery(q2));
    cubeQueries = mockedDriver.findCubePositions(q2);
    Assert.assertEquals(cubeQueries.size(), 1);
    Assert.assertEquals(cubeQueries.get(0).query, "cube select name2 from table2");
    mockedDriver.rewriteQuery(q2);

    q2 = "select u.* from (select name from table union all cube select name2 from table2)u";
    Assert.assertTrue(cubeDriver.isCubeQuery(q2));
    cubeQueries = mockedDriver.findCubePositions(q2);
    Assert.assertEquals(cubeQueries.size(), 1);
    Assert.assertEquals(cubeQueries.get(0).query, "cube select name2 from table2");
    mockedDriver.rewriteQuery(q2);

    q2 = "select * from (cube select name from table union all cube select name2" +
      " from table2 union all cube select name3 from table3) u";
    Assert.assertTrue(cubeDriver.isCubeQuery(q2));
    cubeQueries = mockedDriver.findCubePositions(q2);
    mockedDriver.rewriteQuery(q2);
    Assert.assertEquals(cubeQueries.size(), 3);
    Assert.assertEquals(cubeQueries.get(0).query, "cube select name from table");
    Assert.assertEquals(cubeQueries.get(1).query, "cube select name2 from table2");
    Assert.assertEquals(cubeQueries.get(2).query, "cube select name3 from table3");
    mockedDriver.rewriteQuery(q2);

    q2 = "select * from   (     cube select name from table    union all   cube" +
      " select name2 from table2   union all  cube select name3 from table3 )  u";
    Assert.assertTrue(cubeDriver.isCubeQuery(q2));
    cubeQueries = mockedDriver.findCubePositions(q2);
    mockedDriver.rewriteQuery(q2);
    Assert.assertEquals(cubeQueries.size(), 3);
    Assert.assertEquals(cubeQueries.get(0).query, "cube select name from table");
    Assert.assertEquals(cubeQueries.get(1).query, "cube select name2 from table2");
    Assert.assertEquals(cubeQueries.get(2).query, "cube select name3 from table3");
    mockedDriver.rewriteQuery(q2);

    q2 = "select * from (cube select name from table union all cube select" +
         " name2 from table2) u group by u.name";
    Assert.assertTrue(cubeDriver.isCubeQuery(q2));
    cubeQueries = mockedDriver.findCubePositions(q2);
    Assert.assertEquals(cubeQueries.size(), 2);
    Assert.assertEquals(cubeQueries.get(0).query, "cube select name from table");
    Assert.assertEquals(cubeQueries.get(1).query, "cube select name2 from table2");
    mockedDriver.rewriteQuery(q2);

    q2 = "select * from (cube select name from table union all cube select" +
        " name2 from table2)  u group by u.name";
   Assert.assertTrue(cubeDriver.isCubeQuery(q2));
   cubeQueries = mockedDriver.findCubePositions(q2);
   mockedDriver.rewriteQuery(q2);
   Assert.assertEquals(cubeQueries.size(), 2);
   Assert.assertEquals(cubeQueries.get(0).query, "cube select name from table");
   Assert.assertEquals(cubeQueries.get(1).query, "cube select name2 from table2");
   mockedDriver.rewriteQuery(q2);
  }
  
  @Test
  public void testCubeDriverReadWrite() throws Exception {
    // Test read/write for cube driver
    CubeGrillDriver cubeDriver = new CubeGrillDriver(conf);
    ByteArrayOutputStream driverOut = new ByteArrayOutputStream();
    ObjectOutputStream out = new ObjectOutputStream(driverOut);
    cubeDriver.writeExternal(out);
    out.close();
    System.out.println(Arrays.toString(driverOut.toByteArray()));
    
    ByteArrayInputStream driverIn = new ByteArrayInputStream(driverOut.toByteArray());
    conf.setInt("mock.driver.test.val", -1);
    CubeGrillDriver newDriver = new CubeGrillDriver(conf);
    newDriver.readExternal(new ObjectInputStream(driverIn));
    driverIn.close();
    Assert.assertEquals(newDriver.getDrivers().size(), cubeDriver.getDrivers().size());
    
    for (GrillDriver driver : newDriver.getDrivers()) {
      if (driver instanceof MockDriver) {
        MockDriver md = (MockDriver) driver;
        Assert.assertEquals(md.getTestIOVal(), 5);
      }
    }
  }
}
