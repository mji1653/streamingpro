/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package streaming.test.stream

import org.apache.spark.streaming.BasicSparkOperation
import org.scalatest.BeforeAndAfterAll
import streaming.common.shell.ShellCommand
import streaming.core.strategy.platform.SparkRuntime
import streaming.core.{BasicMLSQLConfig, SpecFunctions, StreamingproJobManager, StreamingproJobType}
import streaming.dsl.ScriptSQLExec
import streaming.log.Logging

class Stream3Spec extends BasicSparkOperation with SpecFunctions with BasicMLSQLConfig with BeforeAndAfterAll with Logging {

  "manager stream jobs" should "work fine " in {
    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      // we suppose that if KAFKA_HOME is configured ,then there must be a kafka server exists
      ShellCommand.execCmd("rm -rf /tmp/cpl3")

      val ssel = createSSELWithJob(spark, "streamExample2", "jack")
      ScriptSQLExec.parse(
        s"""
           |-- the stream name, should be uniq.
           |set streamName="streamExample";
           |
           |set data='''
           |{"key":"yes","value":"a,b,c","topic":"test","partition":0,"offset":0,"timestamp":"2008-01-24 18:01:01.001","timestampType":0}
           |{"key":"yes","value":"d,f,e","topic":"test","partition":0,"offset":1,"timestamp":"2008-01-24 18:01:01.002","timestampType":0}
           |{"key":"yes","value":"k,d,j","topic":"test","partition":0,"offset":2,"timestamp":"2008-01-24 18:01:01.003","timestampType":0}
           |{"key":"yes","value":"m,d,z","topic":"test","partition":0,"offset":3,"timestamp":"2008-01-24 18:01:01.003","timestampType":0}
           |{"key":"yes","value":"o,d,d","topic":"test","partition":0,"offset":4,"timestamp":"2008-01-24 18:01:01.003","timestampType":0}
           |{"key":"yes","value":"m,m,m","topic":"test","partition":0,"offset":5,"timestamp":"2008-01-24 18:01:01.003","timestampType":0}
           |''';
           |
           |load jsonStr.`data` as datasource;
           |
           |load mockStream.`datasource` options
           |stepSizeRange="0-3"
           |and valueFormat="csv"
           |and valueSchema="st(field(column1,string),field(column2,string),field(column3,string))"
           |as newkafkatable1;
           |
           |select column1,column2,column3,kafkaValue from newkafkatable1
           |as table21;
           |
           |save append table21
           |as console.``
           |options mode="Append"
           |and duration="1"
           |and checkpointLocation="/tmp/cpl3";
         """.stripMargin, ssel)
      Thread.sleep(1000 * 15)
      StreamingproJobManager.removeJobManually("jack")
      assume(spark.streams.active.size > 0)
      val streamQuery = spark.streams.active.head
      val streamJob = StreamingproJobManager.getJobInfo.filter(f => f._2.jobType == StreamingproJobType.STREAM).head
      spark.streams.active.foreach(f => f.stop())
      assume(streamJob._2.jobName == "streamExample")
      assume(streamJob._2.groupId == streamQuery.id.toString)

    }
  }

}
