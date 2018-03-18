# SMM-with-Spark-Streaming

Recent times have seen remarkable increase in number of mobile devices connected to the Internet and a variety of uses based on their location data have come into existence. This increase in number necessitates a scalable map-matching solution to meet the ever-increasing demand. Map Matching is the problem of how to match recorded geographic coordinates to a logical model of the real world. Thereby, measured GPS coordinates are matched onto a reference coordinate system (e.g. a map). The existing solutions for map matching do not scale and therefore are no suitable for Big Data. This project is mainly based on research conducted by Newson & Krumm and Mattheis et al. Based on the above, this project is aimed at developing a map matching application that runs on Apache Spark Streaming, where the map matching is done at scale and in a streaming fashion. 

This project is based on BMWCarIT's Barefoot library.
Check out the source here: https://github.com/bmwcarit/barefoot

The final report can be found here: 
https://github.com/achintya-kumar/SMM-with-Spark-Streaming/blob/master/Scalable_Map_Matching_Final_Report.pdf

## Setup
This project is composed in Cloudera Quickstart VM. 
1. Install Maven and Java 8. (Set ```JAVA_HOME``` if required)<br />
2. As mentioned above, the project requires Barefoot library jar as a Maven dependency. It is therefore necessary that Barefoot is downloaded and installed as follows:<br />
```git clone https://github.com/bmwcarit/barefoot.git```<br />
```cd barefoot```<br />
```mvn clean install -DskipTests``` 
3. Now the project can be built
```mvn clean package```<br />
      Add ```-q``` to build silently. 
