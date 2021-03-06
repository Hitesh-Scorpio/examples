/**
 * Copyright (c) 2016 DataTorrent, Inc.
 * All rights reserved.
 */
package com.datatorrent.cassandra;

import java.io.IOException;

import javax.validation.ConstraintViolationException;

import org.apache.hadoop.conf.Configuration;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datatorrent.api.LocalMode;
import com.datatorrent.contrib.cassandra.CassandraTransactionalStore;
import com.datatorrent.netlet.util.DTThrowable;

public class CassandraApplicationTest
{
  private static final String NODE = "localhost";
  private static final String KEYSPACE = "testapp";
  private static final String TABLE_NAME = "AppTestTable";
  private static Cluster cluster = null;
  private static Session session = null;

  @BeforeClass
  public static void setup()
  {
    try {
      cluster = Cluster.builder().addContactPoint(NODE).build();
      session = cluster.connect(KEYSPACE);

      String createMetaTable = "CREATE TABLE IF NOT EXISTS " + KEYSPACE + "."
          + CassandraTransactionalStore.DEFAULT_META_TABLE + " ( " + CassandraTransactionalStore.DEFAULT_APP_ID_COL
          + " TEXT, " + CassandraTransactionalStore.DEFAULT_OPERATOR_ID_COL + " INT, "
          + CassandraTransactionalStore.DEFAULT_WINDOW_COL + " BIGINT, " + "PRIMARY KEY ("
          + CassandraTransactionalStore.DEFAULT_APP_ID_COL + ", " + CassandraTransactionalStore.DEFAULT_OPERATOR_ID_COL
          + ") " + ");";
      session.execute(createMetaTable);
      String createTable = "CREATE TABLE IF NOT EXISTS "
          + KEYSPACE
          + "."
          + TABLE_NAME
          + " (id uuid PRIMARY KEY,age int,lastname text,test boolean,floatvalue float,doubleValue double,set1 set<int>,list1 list<int>,map1 map<text,int>,last_visited timestamp);";
      session.execute(createTable);
    } catch (Throwable e) {
      DTThrowable.rethrow(e);
    }
  }

  @AfterClass
  public static void cleanup()
  {
    if (session != null) {
      session.execute("DROP TABLE " + CassandraTransactionalStore.DEFAULT_META_TABLE);
      session.execute("DROP TABLE " + KEYSPACE + "." + TABLE_NAME);
      session.close();
    }
    if (cluster != null) {
      cluster.close();
    }
  }

  @Test
  public void testApplication() throws IOException, Exception
  {
    try {
      LocalMode lma = LocalMode.newInstance();
      Configuration conf = new Configuration(false);
      conf.addResource(this.getClass().getResourceAsStream("/properties-CassandraOutputTestApp.xml"));
      conf.set("dt.operator.CassandraDataWriter.prop.store.node", "localhost");
      conf.set("dt.operator.CassandraDataWriter.prop.store.keyspace", KEYSPACE);
      conf.set("dt.operator.CassandraDataWriter.prop.tablename", TABLE_NAME);
      lma.prepareDAG(new Application(), conf);
      LocalMode.Controller lc = lma.getController();
      lc.run(10000); // runs for 10 seconds and quits

      //validate: Cassandra provides eventual consistency so not checking for exact record count.
      String recordsQuery = "SELECT * from " + KEYSPACE + "." + TABLE_NAME + ";";
      ResultSet resultSetRecords = session.execute(recordsQuery);
      Assert.assertTrue("No records were added to the table.", resultSetRecords.getAvailableWithoutFetching() > 0);
    } catch (ConstraintViolationException e) {
      Assert.fail("constraint violations: " + e.getConstraintViolations());
    }
  }
}
