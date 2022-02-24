# Example Spring Cloud Config Server on Tanzu Application Platform

The purpose of this repository is demonstrate how to configure a simple Spring Cloud Config Server to run on Tanzu Application Platform and use Service Bindings to consume secrets used by: 

- [Git Backend Http Basic Authentication](https://cloud.spring.io/spring-cloud-config/reference/html/#_git_backend)
- [Configure Symmetric Key used for Encryption](https://cloud.spring.io/spring-cloud-config/reference/html/#_key_management)

## Configure the Secrets in Kubernetes

Kubernetes already provides a means to securely store seed [secrets](https://kubernetes.io/docs/concepts/configuration/secret/) such as git authentication ane symmetric keys.


### configserver-git-auth secret

Create a Kubernetes secret for `configserver-git-auth`.  This secrets `stringData` will be shapped based upon the [servicebinding/spec](https://github.com/servicebinding/spec/tree/12a9f2e376c50f051cc9aa913443bdecb0a24a01#well-known-secret-entries).  The only required field for our binding is the `type` field which must be equal to `configserver-git-auth`.  This will be used by the `GitAuthBindingsPropertiesProcessor` to detect that these properties are bound to the application.

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: configserver-git-auth
stringData:
  type: configserver-git-auth
  uri: https://github.com/spring-cloud-samples/config-repo.git
  username: <optional username associated with personal access token>
  password: <optional personal access token https://github.com/settings/tokens>
  # skipSslValidation: false
```

| Key      | Description |
| ----------- | ----------- |
| type      | REQUIRED and MUST be set to `configserver-git-auth`       |
| uri   | value that will map to `spring.cloud.config.server.git.uri` property |
| username | (optional) value that will map to `spring.cloud.config.server.git.username` property |
| password | (optional) value that will map to `spring.cloud.config.server.git.password` property |
| skipSslValidation | (optional) value that will map to `spring.cloud.config.server.git.skipSslValidation` property |

Apply secret to the kubernetes cluster:
```bash
kubectl apply -f secret-configserver-git-auth.yml
```

### configserver-encrypt-key secret

Create a Kubernetes secret for `configserver-encrypt-key`.  This secrets `stringData` will be shapped based upon the [servicebinding/spec](https://github.com/servicebinding/spec/tree/12a9f2e376c50f051cc9aa913443bdecb0a24a01#well-known-secret-entries).  The only required field for our binding is the `type` field which must be equal to `configserver-encrypt-key`.  This will be used by the `EncryptKeyBindingsPropertiesProcessor` to detect that these properties are bound to the application.

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: configserver-encrypt-key
stringData:
  type: configserver-encrypt-key
  key: |- 
    -----BEGIN PRIVATE KEY-----
    MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDuw5SY8zb0lOO0
    3Ky3pHfHGUMQDQv/KDDpdG2wWz+gcrp21ewI0ZNEYJpc/hrzdkx2EP9+EI/0DrlT
    ...
    r9vod9KGSTpV0Z/VHkpaB0gpcCVXKhKa5fm8LH146zELAElMzzaIYB5uCzAPVvu5
    Ylr/anVxGDHmBNLxtO6MlZ5F
    -----END PRIVATE KEY-----
```
| Key      | Description |
| ----------- | ----------- |
| type      | REQUIRED and MUST be set to `configserver-encrypt-key`       |
| key   | multi-line value that will map to `encrypt.key` property must be a `symmetric (shared) key` generated using command `openssl genpkey -algorithm RSA` |

Apply secret to the kubernetes cluster:
```bash
kubectl apply -f secret-configserver-encrypt-key.yml
```

## Service Binding

To present the secrets to the application running on Tanzu Application Platform a `ServiceBinding` object should be created for each secret.  

### configserver-git-auth binding

Below is a [Direct Secret Reference](https://github.com/servicebinding/spec/tree/12a9f2e376c50f051cc9aa913443bdecb0a24a01#direct-secret-reference-example-resource) ServiceBinding resource that will associate the Deployment that matches the label `carto.run/workload-name` equal to `configserver`. It will then bind the secret with name `configserver-git-auth` to the application.  

```yaml
apiVersion: servicebinding.io/v1alpha3
kind: ServiceBinding
metadata:
  name: configserver-git-auth

spec:
  workload:
    apiVersion: apps/v1
    kind: Deployment
    selector:
      matchLabels:
        carto.run/workload-name: configserver

  service:
    apiVersion: v1
    kind: Secret
    name: configserver-git-auth
```

Apply the service binding to the kubernetes cluster:

```bash
kubectl apply -f configserver-git-auth-service-binding.yml
```

```yaml
apiVersion: servicebinding.io/v1alpha3
kind: ServiceBinding
metadata:
  name: configserver-encrypt-key

spec:
  workload:
    apiVersion: apps/v1
    kind: Deployment
    selector:
      matchLabels:
        carto.run/workload-name: configserver

  service:
    apiVersion: v1
    kind: Secret
    name: configserver-encrypt-key
```

### Validate the ServiceBinding Resources

ServiceBinding resources should exist now in the kubernetes cluster.  Validate the status of them using the following:

```bash
kubectl get ServiceBindings
```

This will return the following results:
```bash
NAME                         READY   REASON   AGE
configserver-encrypt-key     True    Ready    3s
configserver-git-auth        True    Ready    3s
```

## Apply the Workload

Apply the workload in `config/workload.yaml`.  The service bindings will be added to the `Deployment` generated by Tanzu Application Platform that match.

```bash
kubectl apply -f config/workload.yaml
```

### Inspect the Deployment

When the workload becomes ready you should be able to view the secrets have been bound to the application as `volumes`.  Inspect the deployment by getting the generated deployment yaml from the kubernetes cluster: 

```bash
kubectl get deployments -l carto.run/workload-name=configserver -o yaml
```
The volumes section should now be present and look like the following: 

```yaml
volumes:
- name: binding-0017848bc7508a75a30990c58c2051be2312a3a6
    projected:
    defaultMode: 420
    sources:
    - secret:
        name: configserver-git-auth
- name: binding-035ae7564070c6c7fe694e195e374cbe64a1eff2
    projected:
    defaultMode: 420
    sources:
    - secret:
        name: configserver-encrypt-key
```

## How Spring Loads the Secrets in the Binding

The spring team has a project [spring-cloud-bindings](https://github.com/spring-cloud/spring-cloud-bindings).  The project states that its goals are: 
> The Spring Cloud Bindings library exposes a rich Java language binding for the Cloud Native Buildpacks Binding Specification. In addition, if opted-in, it configures Spring Boot application configuration properties appropriate for the type of binding encountered.

In the case of our config server we can use this project to write our own `org.springframework.cloud.bindings.boot.BindingsPropertiesProcessor`. Following the documentation [Extending Spring Boot Configuration](https://github.com/spring-cloud/spring-cloud-bindings#extending-spring-boot-configuration) we are able to implement our own `BindingsPropertiesProcessor` to load secrets to authenticate to git and the encryption key we use to decrypt and encrypt secrets stored in git.

### EncryptKeyBindingsPropertiesProcessor

The `EncryptKeyBindingsPropertiesProcessor` implementation is responsible for processing secrets with a `type` key equal to `configserver-encrypt-key`. It will do the work to map the necessary secret keys to well known spring cloud config server property `encrypt.key` to configure symetric key used to decrypt and encrypt secrets.

### GitAuthBindingsPropertiesProcessor

The `GitAuthBindingsPropertiesProcessor` implementation is responsible for processing secrets with a `type` key equal to `configserver-git-auth`.  It will map the keys in our Secret to well know Spring Cloud Config Server properties.

### META-INF/spring.factories

We also need to ensure that a `META-INF/spring.factories` file is present in our `src/main/resources` which will inform Spring that our `BindingPropertiesProcessor` implementations can be discovered.

### pom.xml

Add the spring-cloud-bindings dependency:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-bindings</artifactId>
    <version>${spring-cloud-bindings.version}</version>
</dependency>
```

Include the `spring-releases` repository: 

```xml
<repository>
    <id>spring-releases</id>
    <url>https://repo.spring.io/release/</url>
    <releases>
        <enabled>true</enabled>
    </releases>
    <snapshots>
        <enabled>false</enabled>
    </snapshots>
</repository>
```
Also, be sure to update the `spring-boot-maven-plugin` to exclude the `spring-cloud-bindings` jar because it is already contributed to the application using `cloud-native-buildpacks`

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <excludes>
            <exclude>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-bindings</artifactId>
            </exclude>
        </excludes>
    </configuration>
</plugin>
```

### How It Works

The class [BindingSpecificEnvironmentPostProcessor](https://github.com/spring-cloud/spring-cloud-bindings/blob/main/src/main/java/org/springframework/cloud/bindings/boot/BindingSpecificEnvironmentPostProcessor.java#L100) will discover all registered implementations of `BindingPropertiesProcessor` and pass the environment, bindings, and properties to them.  It is the Processors job to only process bindings that match their configured `type`.  Each property that is read in will then be contributed as [PropertySource](https://github.com/spring-cloud/spring-cloud-bindings/blob/main/src/main/java/org/springframework/cloud/bindings/boot/BindingSpecificEnvironmentPostProcessor.java#L107).

## Closing 

The approach of using `BindingPropertiesProcessor` is a Developer Experience improvement.  It provides developers with the means to discover securely shared secrets with the application.  It provides a simplified means for Operators to configure the secrets and bind them to one or more applications running on the platform.  The Tanzu Application Platform will take care of the heavy lifting of generating the deployment and ensuring the volumes containing the secrets are mounted.  Spring Cloud Bindings will take care of the heavy lifting of discovering all of the Bindings that have been made to the application and presenting them as candiates to be processed.  The `BindingPropertiesProcessor` implementation is all the developer will need to contribute to map the values to well known properties that their application is looking for be it for database, git, backing service, oauth, etc.  Check out the [spring-cloud-bindings](https://github.com/spring-cloud/spring-cloud-bindings) for bindings that are already available out of the box and contribute. Also, look to write your own like we have done here. Oh! and this is built into the Cloud Native Buildpacks by default.

## Setting up TAP Locally

This documentation was assembly by using my guide [Tanzu Application Platform Local Setup](https://github.com/xyloman/tanzu-application-platform-local-setup) in order to setup TAP Locally.