/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.iotweatherstation;

import com.google.cloud.bigtable.dataflow.CloudBigtableIO;
import com.google.cloud.bigtable.dataflow.CloudBigtableOptions;
import com.google.cloud.bigtable.dataflow.CloudBigtableTableConfiguration;
import com.google.cloud.dataflow.sdk.Pipeline;
import com.google.cloud.dataflow.sdk.coders.CoderRegistry;
import com.google.cloud.dataflow.sdk.io.PubsubIO;
import com.google.cloud.dataflow.sdk.options.DataflowPipelineOptions;
import com.google.cloud.dataflow.sdk.options.PipelineOptionsFactory;
import com.google.cloud.dataflow.sdk.transforms.DoFn;
import com.google.cloud.dataflow.sdk.transforms.ParDo;

import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

/**
 * This class reads data sent from IoT devices from PubSub and writes it to Bigtable.
 * <p>
 * Configure the pipeline with these command line options:
 * <pre>
 *     --bigtableProjectId=[bigtable project]
 *     --bigtableInstanceId=[bigtable instance id]
 *     --bigtableClusterId=[bigtable cluster id]
 *     --bigtableZoneId=[bigtable zone]
 *     --bigtableTableId=[bigtable table name]
 *     --stagingLocation=gs://[cloud storage bucket]
 *     --pubsubTopic=projects/[project name]/topics/[topic name]
 * </pre>
 * This example cannot be run locally using DirectPipelineRunner because PubsubIO won't work.
 */
public class DeviceDataIngest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceDataIngest.class);

    private static final byte[] DATAFAMILY = Bytes.toBytes("data"); // Bigtable column family

    static final DoFn<JSONObject, Mutation> MUTATION_TRANSFORM = new DoFn<JSONObject, Mutation>() {
        private static final long serialVersionUID = 1L;

        @Override
        public void processElement(DoFn<JSONObject, Mutation>.ProcessContext c) throws Exception {
            JSONObject element = c.element();
            String deviceId = element.getString("deviceId");
            String channel = element.getString("channel");
            long timestamp = element.getLong("timestamp");
            JSONObject data = element.getJSONObject("data");

            // Create the row key for Bigtable
            String rowKey = deviceId + "#" + channel + "#" + (Long.MAX_VALUE - timestamp);
            LOGGER.info("[processElement] New row key: {}", rowKey);
            // Create the Put request.
            Put p = new Put(rowKey.getBytes());

            // Add the data keys as columns to the row.
            Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                byte[] val = data.getString(key).getBytes();
                p.addColumn(DATAFAMILY, key.getBytes(), val);
            }

            // Add the Put request to the mutation
            c.output(p);
        }
    };

    /**
     * The options class for this Pipeline. Contains the PubSub topic and options for Bigtable.
     */
    public interface BigtablePubsubOptions extends CloudBigtableOptions {
        String getPubsubTopic();

        void setPubsubTopic(String pubsubTopic);
    }

    /**
     * Creates a Dataflow Pipeline that performs the following steps:
     * <ol>
     * <li> Reads from a Cloud Pubsub topic
     * <li> Transforms the data from JSON to a Bigtable mutation.
     * <li> Performs a Bigtable Put on the items
     * </ol>
     *
     * Configure the pipepline with command line arguments as follows:
     * <pre>
     *     --bigtableProjectId=[bigtable project]
     *     --bigtableInstanceId=[bigtable instance id]
     *     --bigtableClusterId=[bigtable cluster id]
     *     --bigtableZoneId=[bigtable zone]
     *     --bigtableTableId=[bigtable table name]
     *     --stagingLocation=gs://[cloud storage bucket]
     *     --pubsubTopic=projects/[project name]/topics/[topic name]
     * </pre>
     *
     * @param args Arguments to use to configure the Dataflow Pipeline.
     */
    public static void main(String[] args) throws Exception {
        // CloudBigtableOptions is one way to retrieve the options. It's not required.
        BigtablePubsubOptions options = PipelineOptionsFactory.fromArgs(args).withValidation().as(
                BigtablePubsubOptions.class);

        // Contains the project, zone, cluster and table to connect to.
        CloudBigtableTableConfiguration config = CloudBigtableTableConfiguration.fromCBTOptions(
                options);

        // Enable streaming.
        options.as(DataflowPipelineOptions.class).setStreaming(true);

        Pipeline pipeline = Pipeline.create(options);

        // Register our JSONCoder class so that JSONObjects can be serialized.
        CoderRegistry cr = pipeline.getCoderRegistry();
        cr.registerCoder(JSONObject.class, JSONCoder.class);

        // This sets up serialization for Puts and Deletes so that Dataflow can
        // potentially move them through the network
        CloudBigtableIO.initializeForWrite(pipeline);

        // Create the pipeline
        pipeline
            .apply(PubsubIO.Read.topic(options.getPubsubTopic()).withCoder(JSONCoder.of()))
            .apply(ParDo.of(MUTATION_TRANSFORM))
            .apply(CloudBigtableIO.writeToTable(config));

        pipeline.run();
    }
}
