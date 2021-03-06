/*
 * Copyright 2017 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.dbeam

import java.sql.Connection

import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest._
import slick.jdbc.H2Profile.api._


class PsqlAvroJobTest extends FlatSpec with Matchers with BeforeAndAfterAll {
  private val connectionUrl: String =
    "jdbc:h2:mem:testpsql;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
  private val db: Database = Database.forURL(connectionUrl, driver = "org.h2.Driver")
  private val connection: Connection = db.source.createConnection()

  override def beforeAll(): Unit = {
    JdbcTestFixtures.createFixtures(db, Seq(JdbcTestFixtures.record1))
  }

  it should "fail with invalid driver" in {
    val options = SqlAvroOptions(
      "com.mysql.jdbc.Driver",
      "jdbc:mysql://nonsense",
      "dbeam-extractor",
      "secret",
      "some_table",
      "/path",
      "dbeam_generated"
    )

    a[IllegalArgumentException] should be thrownBy {
      PsqlAvroJob.validateOptions(options)
    }
  }

  it should "fail with missing partition" in {
    val options = SqlAvroOptions(
      "org.postgresql.Driver",
      "jdbc:postgresql://nonsense",
      "dbeam-extractor",
      "secret",
      "some_table",
      "/path",
      "dbeam_generated"
    )

    a[IllegalArgumentException] should be thrownBy {
      PsqlAvroJob.validateOptions(options)
    }
  }

  it should "validate" in {
    val options = SqlAvroOptions(
      "org.postgresql.Driver",
      "jdbc:postgresql://nonsense",
      "dbeam-extractor",
      "secret",
      "some_table",
      "/path",
      "dbeam_generated",
      partition = Some(new DateTime(2027, 7, 31, 0, 0, DateTimeZone.UTC))
    )

    PsqlAvroJob.validateOptions(options)
  }

  it should "validate replication" in {
    val partition = new DateTime(2027, 7, 31, 0, 0, DateTimeZone.UTC)
    val lastReplication = new DateTime(2027, 8, 1, 0, 0, DateTimeZone.UTC)

    val actual = PsqlAvroJob.validateReplication(partition, lastReplication)

    actual should be (partition)
  }

  ignore should "fail on too replication" in {
    val partition = new DateTime(2027, 7, 31, 0, 0, DateTimeZone.UTC)
    val lastReplication = new DateTime(2027, 7, 19, 0, 0, DateTimeZone.UTC)

    a[IllegalArgumentException] should be thrownBy {
      PsqlAvroJob.validateReplication(partition, lastReplication)
    }
  }

  it should "run query" in {
    val query = "SELECT " +
      "parsedatetime('2017-02-01 23.58.57 UTC', 'yyyy-MM-dd HH.mm.ss z', 'en', 'UTC')" +
      " AS last_replication, " +
      "13 AS replication_delay"
    val lastReplication = new DateTime(2017, 2, 1, 23, 58, 57, DateTimeZone.UTC)

    val actual = PsqlAvroJob.queryReplication(connection, query)

    new DateTime(actual, DateTimeZone.UTC) should be (lastReplication)
  }

}
