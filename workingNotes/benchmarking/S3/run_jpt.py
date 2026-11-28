# Avvia jpt_service/service.py senza modificarlo: importa torch prima di tensorflow.
# Con l'ordine originale (tensorflow -> torch) il processo va in abort con
# "random_device could not be read" (conflitto tra le librerie native di TF 2.13 e torch 2.0.1).
import runpy
import sys

import torch  # noqa: F401

sys.path.insert(0, ".")
runpy.run_path("service.py", run_name="__main__")
