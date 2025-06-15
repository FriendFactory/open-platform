# operation Project

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

This project uses JDK-17, and to build it, Maven is needed. In order to build a native image for Linux, Docker is needed.

The purpose of this project is for various tasks of Platform operation. Right now, it's doing:

1. Checking long running queries (longer than 70 seconds) in main, video and auth PostgreSQL RDS in production environment.
2. Checking long opening transactions (longer than 10 minutes) in main, video and auth PostgreSQL RDS in production environment.

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

You can create a native executable under current environment using:

```shell script
./mvnw package -Pnative -Dquarkus.native.container-build=false
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using (this will create Linux x86_64 executable):

```shell script
./mvnw package -Pnative
```

You can then execute your native executable with: `./target/frever-platform-operation`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.

## Related Guides

- RESTEasy Reactive ([guide](https://quarkus.io/guides/resteasy-reactive)): A JAX-RS implementation utilizing build time
  processing and Vert.x. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions
  that depend on it.

## Provided Code

### RESTEasy Reactive

Easily start your Reactive RESTful Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)

## Provision

This app is currently running on an EC2 instance named `platform-operation` with IP `10.0.1.15`.

The infrastructure is completely setup using Terraform under `platform/operation/terraform/eu-central-1`.

To provision the app on the EC2 instance, run `ansible-playbook ansible/install.yml -i platform-operation, --user ec2-user` under `platform/operation` folder.
