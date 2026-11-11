"""Generate ignored IntelliJ configurations from resolved Compose settings.

Input is `docker compose config --format json`; never print credentials.
"""
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
config = json.load(sys.stdin)
services = config['services']
apps = {name: svc for name, svc in services.items() if svc.get('build')}
addresses = {}
for name, svc in services.items():
    for port in svc.get('ports', []):
        if port.get('published'):
            host_port = port['target'] if name in apps else port['published']
            addresses[(name, str(port['target']))] = str(host_port)

def host_value(value):
    value = str(value)
    for (name, internal), published in addresses.items():
        value = value.replace(f'{name}:{internal}', f'127.0.0.1:{published}')
        value = value.replace(f'lb://{name}', f'http://127.0.0.1:{published}')
    if value in services:
        return '127.0.0.1'
    return value

base_env = {}
for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines():
    if line.strip() and not line.lstrip().startswith('#') and '=' in line:
        key, value = line.split('=', 1)
        base_env[key.strip()] = value.strip().strip('"').strip("'")

output = ROOT / '.idea' / 'runConfigurations'
output.mkdir(parents=True, exist_ok=True)
runtime = {}
jdk = str(Path.home() / '.jdks' / 'ms-21.0.10')
if not Path(jdk, 'bin', 'java.exe').exists():
    raise SystemExit('Java 21 installation not found: ' + jdk)

for module, svc in apps.items():
    source = next((ROOT / module / 'src/main/java').rglob('*Application.java'))
    text = source.read_text(encoding='utf-8-sig')
    package = re.search(r'package\s+([\w.]+);', text).group(1)
    main_class = package + '.' + source.stem
    env = dict(base_env)
    env.update({key: host_value(value) for key, value in svc.get('environment', {}).items() if value is not None})
    port = str(svc['ports'][0]['target'])
    env.update(SERVER_PORT=port, EUREKA_SERVICE_URL='http://127.0.0.1:8761/eureka/',
               EUREKA_INSTANCE_HOSTNAME='127.0.0.1', RABBITMQ_HOST='127.0.0.1',
               RABBITMQ_PORT=addresses[('rabbitmq', '5672')])
    # Split host/port settings must be translated together, not only their hostname.
    if module in ('shop-service', 'wallet-service'):
        database = 'shop-postgres' if module == 'shop-service' else 'wallet-postgres'
        env['DB_PORT'] = addresses[(database, '5432')]
    redis = {'matchmaking-service': 'matchmaking-redis', 'shop-service': 'shop-redis'}.get(module)
    if redis:
        env['REDIS_PORT'] = addresses[(redis, '6379')]
    if module == 'auth-service':
        env.update(AUTH_MAIL_HOST='127.0.0.1', AUTH_MAIL_PORT='1026',
                   AUTH_MAIL_SMTP_AUTH='false', AUTH_MAIL_STARTTLS='false',
                   AUTH_RABBIT_HOST='127.0.0.1', AUTH_RABBIT_PORT=addresses[('rabbitmq', '5672')],
                   AUTH_JWT_PRIVATE_KEY_PATH=str(ROOT / '.local/auth/jwt-private.pem'))
    name = 'Local - ' + source.stem
    component = ET.Element('component', name='ProjectRunConfigurationManager')
    run = ET.SubElement(component, 'configuration', default='false', name=name,
                        type='SpringBootApplicationConfigurationType', factoryName='Spring Boot')
    ET.SubElement(run, 'module', name=module)
    for key, value in [('SPRING_BOOT_MAIN_CLASS', main_class), ('WORKING_DIRECTORY', '$PROJECT_DIR$'),
                       ('ALTERNATIVE_JRE_PATH', jdk), ('ALTERNATIVE_JRE_PATH_ENABLED', 'true'),
                       ('VM_PARAMETERS', '-Xms64m -Xmx384m')]:
        ET.SubElement(run, 'option', name=key, value=value)
    envs = ET.SubElement(run, 'envs')
    for key, value in sorted(env.items()):
        ET.SubElement(envs, 'env', name=key, value=value)
    method = ET.SubElement(run, 'method', v='2')
    ET.SubElement(method, 'option', name='Make', enabled='true')
    ET.indent(component)
    ET.ElementTree(component).write(output / (module + '_local.xml'), encoding='utf-8', xml_declaration=True)
    runtime[module] = dict(name=name, env=env, main_class=main_class, port=int(port), jdk=jdk)

component = ET.Element('component', name='ProjectRunConfigurationManager')
compound = ET.SubElement(component, 'configuration', default='false', name='Local - All Services', type='CompoundRunConfigurationType')
for module in sorted(runtime, key=lambda name: name != 'discovery-service'):
    ET.SubElement(compound, 'toRun', name=runtime[module]['name'], type='SpringBootApplicationConfigurationType')
ET.SubElement(compound, 'method', v='2')
ET.indent(component)
ET.ElementTree(component).write(output / 'all_services_local.xml', encoding='utf-8', xml_declaration=True)
(ROOT / '.local').mkdir(exist_ok=True)
(ROOT / '.local/ide-runtime.json').write_text(json.dumps(runtime), encoding='utf-8')
print(f'Generated {len(runtime)} Java 21 run configurations and Local - All Services (credentials remain in ignored local files).')
