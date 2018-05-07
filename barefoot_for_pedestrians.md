### Configuring Barefoot for tracking pedestrians/bicyles

- We are using the .bfmap file which has the map data for motor vehicles which looks for legal routes and detours to the most possible motorway. First delete the existing .bfmap file and generate a new file with one-way information which allows tracking pedestrians/bicycles.

- Follow below steps to generate a .bfmap file with one-way information.
  - Add the below values to the file ***barefoot/map/tools/road-types.json*** to download the required paths.
  ```ruby
            {
              "name":"footway",
              "id":118,
              "priority":1.1,
              "maxspeed":50
            },
            {
              "name":"steps",
              "id":119,
              "priority":1.9,
              "maxspeed":15
            },
            {
              "name":"cycleway",
              "id":120,
              "priority":1.1,
              "maxspeed":80
            },
            {
              "name":"path",
              "id":121,
              "priority":1.1,
              "maxspeed":60
            },
            {
              "name":"track",
              "id":122,
              "priority":1.1,
              "maxspeed":60
            },
            {
              "name":"road",
              "id":123,
              "priority":1.1,
              "maxspeed":60
            },
            {
              "name":"pedestrian",
              "id":124,
              "priority":1.1,
              "maxspeed":30
            }
  ```
  - Run below commands to create a table with one way information.

  ```ruby
  sudo docker exec <container> psql -h localhost -U osmuser -d <database> -c "create table bfmap_ways_pedestrians as table bfmap_ways;"

  sudo docker exec <container> psql -h localhost -U osmuser -d <database> -c "bfmap_ways_pedestrians set reverse=1;"
```
  - Change the table name in ***barefoot/config/oberbayern.properties*** as mentioned below.

  ```ruby
  database.table=bfmap_ways_pedestrians
  ```
  - Then, change the values of the below mentioned properties in ***barefoot/config/tracker.properties*** as mentioned below. This allows the matcher to skip some samples which are too close w.r.t previously map matched point which is highly possible while walking.

  ```ruby
  matcher.sigma=15
  matcher.distance.min=5
  ```
  - Now package Barefoot JAR with the changes.

  ```ruby
  mvn clean package -DskipTests
  ```
  - Start the matcher server using the below command.

  ```
  java -jar target/barefoot-<VERSION>-matcher-jar-with-dependencies.jar --geojson config/server.properties config/oberbayern.properties
  ```
  - This creates a new .bfmap file with pathways for pedestrians/bicyles.


- Once the above configuration is done we can start the Monitor Server and check with some samples collected from footpaths.
