# ml-service

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using: 
```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: 
```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/ml-service-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.

## Related Guides

- RESTEasy Reactive ([guide](https://quarkus.io/guides/resteasy-reactive)): A Jakarta REST implementation utilizing build time processing and Vert.x. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it.

## ComfyUi setup

[ComfyUi](https://github.com/comfyanonymous/ComfyUI) itself is running on several EC2 instances.

These instances belong to 3 auto-scaling group. We have 3 types of ComfyUi setup, 1: LipSync, 2: Pulid, 3: Makeup. These 3 auto-scaling group map to the 3 types.

Right now, we can maximally auto-scale each type to 3 EC2 instances, in order to set a boundary about the costs. Those 9 instances are already created, 3 of them are in running state, serving both ixia-prod and dev traffic. 

The auto-scale mechanism is only used in ixia-prod environment, the logic is:

1. ML service will check the queue length of all running ComfyUI instances every minute, one auto-scaling group after another.
2. If the max queue length of all the running ComfyUI instances within one auto-scaling group exceeds a threshold (3 for LipSync, 5 for Pulid and Makeup) consecutively 3 times (corresponding source code: https://github.com/FriendFactory/ml-service/blob/main/src/main/java/com/frever/ml/comfy/ComfyUiConstants.javaConnect your Github account ), we’ll increase the number of instances by 1 within the corresponding auto-scaling group. If the max queue length exceed twice of the corresponding threshold, auto-scaling will trigger immediately.
3. If the max queue length remains 0 consecutively for 3 times, auto-scaling will kick in, decrease the number of instances by 1. 
4. Autoscaling will not exceed maximal number of instances within each auto-scaling group, which is 3. Autoscaling will not stop the last instance within each auto-scaling group also, so there is at least 1 instance running for each auto-scaling group.
5. There is new auto-scaling instance startup time, and auto-scaling event cooldown time, the combined time is around 10 minutes.
6. Right now, we are using instances in each auto-scaling group in dev environment too, in order to save cost.

This service listens to a SQS queue (dev-ml-service-input-queue for dev, prod-ml-service-input-queue for ixia-prod) for ComfyUi related tasks, which the backend posts ComfyUi transformation messages to. 

Then based on the instances those tasks run on, those messages are divided into 3 ComfyUi task queues, which maps to our 3 types of ComfyUi setup. 

The AWS infrastructure setup code is at  https://github.com/FriendFactory/open-platform/tree/main/platform/machine-learning/ecs-services/prod/eu-central-1/ml-service for prod and ixia-prod environment, https://github.com/FriendFactory/open-platform/tree/main/platform/machine-learning/ecs-services/non-prod/eu-central-1/ml-service for dev environment. 

