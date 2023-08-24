import sys
import yaml


def main():
    """
    Add required fields to the delivery config (e.g. serviceAccount)
    that aren't needed.

    In the future, this may need to be specified or be handled in Keel
    code itself
    """
    delivery_config = yaml.safe_load(sys.stdin)

    # Set service account
    delivery_config['serviceAccount'] = 'keel'
    for environment in delivery_config['environments']:
        for resource in environment['resources']:
            # Set resource metadata
            resource['spec']['metadata'] = {
                'application': delivery_config['application'],
            }

    yaml.dump(delivery_config, sys.stdout)


if __name__ == '__main__':
    main()
