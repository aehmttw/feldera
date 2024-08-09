import os
from itertools import islice
from plumbum.cmd import rpk

# File locations
SCRIPT_DIR = os.path.join(os.path.dirname(__file__))

print('Pushing data to Kafka topic...')
for input in ['customer', 'lineitem', 'nation', 'orders', 'part', 'partsupp', 'region', 'supplier']:
    print(input)
    data_csv = os.path.join(SCRIPT_DIR, 'data/' + input + '.csv')

    with open(data_csv, 'r') as f:
        (rpk['topic', 'create', input])()
        for n_lines in iter(lambda: tuple(islice(f, 1000)), ()):
            (rpk['topic', 'produce', input, '-f', '%v'] << '\n'.join(n_lines).replace(',', ';').replace(' ', '_').replace('|', ','))()