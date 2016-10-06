# IoT Weatherstation Sample

### Setup

Requires an Intel Edison running Brillo 2.0

0. Clone this repository :)
0. Create a new Google Cloud Platform project.
0. Under API Manager, enable the following APIs: Cloud Pub/Sub, Cloud Storage,
Cloud BigTable, Google Dataflow.
0. Under Pub/Sub, create a new topic.
0. Under Storage, create a new bucket (or you can use an existing bucket).
0. Under BigTable, create a new instance (you will also define a cluster and a
zone). In this instance, create a table with a column family named `data`.
0. Create a Service Account. Give it the role Pub/Sub Publisher (or a role with
higher privileges) and furnish a new private key. Save the generated json
credentials.
0. Import the project into Android Studio. Add a file named `credentials.json`
inside `brillo/src/main/res/raw/` with the contents of the credentials you
downloaded in 4.
0. In `build.gradle`, replace the `buildConfigField` values with values from
your project setup.
0. In a terminal, navigate to the `dataflow-pipeline` directory. Deploy the
pipeline to Google Cloud Platform with the following command. Replace values
in square brackets with values from your project setup:
    ```sh
    mvn package exec:exec \
        -DDeviceDataIngest \
        -Dbigtable.projectID=[Project name] \
        -Dbigtable.instanceID=[Bigtable instance] \
        -Dbigtable.clusterID=[Bigtable cluster] \
        -Dbigtable.zone=[Bigtable zone] \
        -Dbigtable.table=[Bigtable table] \
        -Dgs=gs://[Storage bucket] \
        -DpubsubTopic=projects/[project name]/topics/[PubSub topic] \
        -X
    ```
0. Launch the app from Android Studio to your Intel Edison.

### License
Copyright 2016 Google Inc. All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.


