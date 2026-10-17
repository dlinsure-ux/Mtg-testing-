import unittest
from scripts.patch_forge_rionya_telemetry import patch as patch_telemetry
from pathlib import Path
import tempfile
import importlib.util

ROOT = Path(__file__).resolve().parents[1]

def load(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

class RionyaPatchTests(unittest.TestCase):
    def test_commander_patch_once(self):
        m=load("rionya_pilot","scripts/patch_forge_rionya_pilot.py")
        src="prefix\n"+m.ANCHOR+"\nsuffix"
        result=m.patch(src)
        self.assertIn("RIONYA_PILOT commander_cast_priority",result)
        self.assertIn("RIONYA_PILOT precombat_spell",result)
        self.assertIn("RIONYA_PILOT resources",result)
        self.assertIn("RIONYA_PILOT commander_search",result)
        self.assertIn("RIONYA_PILOT precombat_decision",result)
        self.assertRaises(ValueError,m.patch,result)
        self.assertRaises(ValueError,m.patch,"unrelated")

    def test_copy_patch_once(self):
        m=load("rionya_copy","scripts/patch_forge_rionya_copy.py")
        src="prefix\n"+m.ANCHOR+"\n"+m.TARGET_ANCHOR+"\nsuffix"
        result=m.patch(src)
        self.assertEqual(result.count("RIONYA_PILOT copy_target"),2)
        self.assertIn("Scourge of Valkas",result)
        self.assertEqual(result.count("RIONYA_PILOT copy_candidate"),2)
        targeted = result.split(m.TARGET_ANCHOR)[0].split("RIONYA_PILOT copy_target")[-1]
        self.assertIn("aiPlayer.get",result)
        self.assertIn("c.getController() != aiPlayer",result)
        self.assertIn("choice = best;",result)
        self.assertRaises(ValueError,m.patch,result)
        self.assertRaises(ValueError,m.patch,"unrelated")

    def test_workflow_configuration(self):
        s=(ROOT/".github/workflows/commander-ai-lab-build.yml").read_text()
        self.assertIn("scripts/patch_forge_rionya_pilot.py",s)
        self.assertIn("scripts/patch_forge_rionya_copy.py",s)
        self.assertIn("forge.ai.rionyaCommanderFirst=true",s)
        self.assertNotIn("\n  push:",s)
        self.assertIn("workflow_dispatch:",s)
        self.assertIn("random.SystemRandom().sample(fresh,3)",s)
        self.assertIn("len(fresh)>=3",s)

    def test_rionya_telemetry_patch(self):
        src = "before\n        phaseHandler = new PhaseHandler(this);\nafter\n"
        result = patch_telemetry(src)
        self.assertIn("new RionyaTelemetry(this)", result)
        self.assertIn("forge.ai.rionyaTelemetry", result)
        with self.assertRaises(ValueError):
            patch_telemetry(result)

    def test_workflow_enables_telemetry(self):
        from pathlib import Path
        s = Path(".github/workflows/commander-ai-lab-build.yml").read_text()
        self.assertIn("scripts/forge/RionyaTelemetry.java", s)
        self.assertIn("patch_forge_rionya_telemetry.py", s)
        self.assertIn("-Dforge.ai.rionyaTelemetry=true", s)
        self.assertNotIn("\n  push:", s)

if __name__ == "__main__":
    unittest.main()
