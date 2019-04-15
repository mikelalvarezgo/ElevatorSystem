
# Elevator System

Elevator control system for the company Wonderland.  
  
## Problem Specification  
  
Design and implement an elevator control system for the company Wonderland.  
What data structures, interfaces and algorithms will you need?  
Your elevator control system should be able to handle multiple elevators up to 16.  
  
You can use the language of your choice to implement an elevator control system.  
In the end, your control system should provide an interface for:  
  
 1. Querying the state of the elevators (what floor are they on and where they are going)  
 2. Receiving an update about the status of an elevator  I am not sure whIn 
 3. Receiving a pickup request  : The system 
  
Time-stepping the simulation should be possible.  

  ***About the second point, I am not totally sure what it is asked here. My assumption is that in this case the elevator will update it position to the required floor after the elevator is done with the passengers and petitions that is attending. During the process of the update the elevator wont be able to attend pickup requests.*
  
## Solution implemented  
  
The solution implemented is an REST api that contain one endpoint for each of the case uses described. The REST api will use an actor that will simulate the elevator system and manage the elevators. An elevator is also represented by an actor.

  ### Algorithm 
My propose about scheduling elevators is:

-   Elevators that are moving in any direction will pick up and leave passenger in that direction.
-   The direction of an elevator will change when there is no any target floor (either to release a passenger or to pick up one) in the current direction.
-   When there is not passengers to pickup or inside the elevator, the elevator will stop in the last floor that a passenger was released.

The elevatorSystem will choose which elevator is ideal for attending a pickup request following the next criteria:

-  If there are elevators moving in direction to the current floor of the passeger, it will pick the closest.
-   If there are no elevators moving in direction to the current floor of the passeger, it will pick the closest elevator that is in stop state.
-   In the case there is no available elevators or elevator moving in the correct direction, it will pick the closest elevator.
- In case there is no elevator that can pick up passengers ( when all elevators are updating their position), the system will retry to choose an elevator after a determined period.

#### Elevator System Implementation
The ElevatorSystem actor is a proxy between the exterior and the elevators. It process the requests that come from the API. Its responsabilities are:

 - Validate messages
 - Recolect states from the elevator 
 - Treat the pickup request and choose the best option possible for the pickup request.
 - Send the update request to the correspondent elevator.

#### Elevator Implementation

The Elevator actor describes the behaviour of the elevator of the system. The implementation of this actor has followed the scheduling elevator described.

### API
   
 - GET  /elevatorSystem/status   :Get status method
 - POST /elevatorSystem/pickup : Register a petition pickup. The petition is a Json entity with the next format: 
```json
{
  "currentFloor" : 1,  
  "targetFloor": 3  
}`
```
 - POST /elevatorSystem/update : Register a petition for update. The petition is a Json entity with the next format: 
```json
{
  "elevatorId" : 1,  
  "targetFloor": 3  
}
```
## Settings  
The settings of the application can be defined in the application.conf contained in the project. The parameters that can be set are the next:
 - number_of_floors : Number of floors that has the building of the elevator system.
 - floor_time_travel : Time it takes to the elevator to move from a floor to the next.
 - number_of_elevators: Number of elevators of the system.
 - pickup_retry_period: Period of time for retry to get an elevator if there isn't any available.
 - port: Port for launching the API.

## Tests  
  
The application contain unitTest for the controller, the ElevatorSystem actor and the elevator actor. For running the test:

    sbt test

## Run the application  
The application can be launched by tipping this:
```
sbt run

```  

