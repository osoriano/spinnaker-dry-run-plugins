# Keel Examples

## Creating an application

An Spinnaker application must be created before the delivery config.

To create an application, run:

```
./create-application.sh <path-to-application>
```

For example:

```
./create-application.sh ./applications/keel-demo-dry-run.json
```

## Creating a delivery config

Once the application is created, the associated delivery config
can be created by running:

```
./post-delivery-config.sh <path-to-delivery-config>
```

For example:

```
delivery-configs/keel-demo-dry-run.yml
```

## Configuring Spinnaker endpoint

See `configuration.sh` for config values determining the
Spinnaker endpoint that will be used.
