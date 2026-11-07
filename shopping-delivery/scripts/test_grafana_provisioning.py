"""Valida o provisionamento do Grafana (observability-stack/dashboards e alerting) — roda no CI.

Um dashboard ou alerta quebrado não falha em lugar nenhum: o painel fica vazio e o alerta nunca dispara. Estes testes
pegam o que já quebrou na prática ou é fácil de quebrar sem perceber.

Dashboards:
- JSON inválido ou uid duplicado (o Grafana ignora o arquivo);
- datasource com uid que não existe em grafana-datasources.yaml (painel "datasource not found");
- painéis sobrepostos ou fora da grade de 24 colunas;
- rota em http_route="..." que não existe mais nos controllers (renomear um endpoint zera o painel em silêncio);
- link entre dashboards apontando para uid inexistente; home dashboard do compose inexistente;
- target_info filtrado por service_name: o exporter do collector não põe esse label em target_info, então o filtro
  nunca casa (o painel "API reportando" mostrou SEM DADOS com a API no ar).

Alertas:
- condição/refId inexistente, datasource desconhecido, severidade ou duração inválida;
- função de template que o Grafana não tem (ex.: `mul`): a descrição chega com o template cru;
- link para dashboard/painel inexistente; rota inexistente; pasta com o mesmo nome da dos dashboards (o
  provisionamento de alertas roda antes e cria uma segunda pasta "ShopEasy").

    python3 -m unittest discover -s shopping-delivery/scripts -p 'test_grafana_provisioning.py'
"""
import glob
import json
import os
import re
import unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
STACK = os.path.join(ROOT, "observability-stack")
DASH_DIR = os.path.join(STACK, "dashboards")
ALERT_DIR = os.path.join(STACK, "alerting")
DASHBOARD_FOLDER = "ShopEasy"  # grafana-dashboards.yaml
# funções de template de alerta usadas aqui e que existem no Grafana (text/template + as de Prometheus)
TEMPLATE_FUNCS = {"printf", "humanize", "humanize1024", "humanizePercentage", "humanizeDuration", "index", "len",
                  "if", "else", "end", "range", "with", "and", "or", "not", "eq", "ne", "lt", "gt", "le", "ge"}
TARGET_INFO_BY_SERVICE = re.compile(r'target_info\{[^}]*service_name')
CONTROLLERS = os.path.join(ROOT, "shopping-delivery", "src", "main", "java", "com", "sskings", "ecommerce", "controller")

MAPPING = re.compile(r'@(Get|Post|Put|Patch|Delete|Request)Mapping(?:\(\s*(?:value\s*=\s*)?\{?\s*"([^"]*)"[^)]*\)|\(\s*\)|(?=\s))')


def load_dashboards():
    out = {}
    for path in sorted(glob.glob(os.path.join(DASH_DIR, "*.json"))):
        with open(path, encoding="utf-8") as f:
            out[os.path.basename(path)] = json.load(f)
    return out


def provisioned_datasource_uids():
    with open(os.path.join(STACK, "grafana-datasources.yaml"), encoding="utf-8") as f:
        return set(re.findall(r"^\s+uid:\s*(\S+)\s*$", f.read(), re.M))


def api_routes(controllers_dir=CONTROLLERS):
    """Rotas no formato do label http_route (ex.: /api/orders/{id}/payment), lidas dos controllers."""
    routes = set()
    for path in glob.glob(os.path.join(controllers_dir, "**", "*.java"), recursive=True):
        with open(path, encoding="utf-8") as f:
            src = f.read()
        head, _, body = src.partition(" class ")
        prefix = ""
        for kind, value in MAPPING.findall(head):
            if kind == "Request":
                prefix = value or ""
        for kind, value in MAPPING.findall(body):
            if kind == "Request":
                continue
            route = prefix + (value or "")
            routes.add(route or "/")
    return routes


def load_alert_groups():
    groups = []
    for path in sorted(glob.glob(os.path.join(ALERT_DIR, "*.json"))):
        with open(path, encoding="utf-8") as f:
            doc = json.load(f)
        assert doc.get("apiVersion") == 1, path
        groups.extend(doc["groups"])
    return groups


def alert_rules():
    for g in load_alert_groups():
        for r in g["rules"]:
            yield g, r


def targets(dashboard):
    for panel in dashboard["panels"]:
        for target in panel.get("targets", []):
            yield panel, target


class DashboardTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dashboards = load_dashboards()

    def test_there_are_dashboards(self):
        self.assertGreaterEqual(len(self.dashboards), 2)

    def test_uids_are_unique_and_titles_present(self):
        uids = [d["uid"] for d in self.dashboards.values()]
        self.assertEqual(len(uids), len(set(uids)), uids)
        for name, d in self.dashboards.items():
            self.assertTrue(d.get("title"), name)
            self.assertLessEqual(len(d["uid"]), 40, name)

    def test_datasources_exist(self):
        known = provisioned_datasource_uids()
        self.assertEqual(known, {"prometheus", "tempo", "loki"})
        for name, d in self.dashboards.items():
            for panel in d["panels"]:
                for ds in [panel.get("datasource")] + [t.get("datasource") for t in panel.get("targets", [])]:
                    if ds:
                        self.assertIn(ds["uid"], known, f"{name}: {panel['title']}")

    def test_panels_fit_the_grid_without_overlap(self):
        for name, d in self.dashboards.items():
            ids, cells = set(), set()
            for p in d["panels"]:
                self.assertNotIn(p["id"], ids, f"{name}: id {p['id']} repetido")
                ids.add(p["id"])
                g = p["gridPos"]
                self.assertLessEqual(g["x"] + g["w"], 24, f"{name}: {p.get('title')}")
                for x in range(g["x"], g["x"] + g["w"]):
                    for y in range(g["y"], g["y"] + g["h"]):
                        self.assertNotIn((x, y), cells, f"{name}: '{p.get('title')}' sobrepõe outro painel")
                        cells.add((x, y))

    def test_ref_ids_unique_per_panel(self):
        for name, d in self.dashboards.items():
            for p in d["panels"]:
                refs = [t["refId"] for t in p.get("targets", [])]
                self.assertEqual(len(refs), len(set(refs)), f"{name}: {p['title']}")

    def test_queries_only_use_backend_service(self):
        for name, d in self.dashboards.items():
            for panel, t in targets(d):
                q = t.get("expr") or t.get("query") or ""
                for svc in re.findall(r'service[._]name\s*=\s*"([^"]+)"', q):
                    self.assertEqual(svc, "backend-spring", f"{name}: {panel['title']}")

    def test_routes_in_queries_exist_in_controllers(self):
        routes = api_routes()
        self.assertIn("/api/orders/checkout", routes)  # sanidade do parser
        self.assertIn("/api/payments/pix/webhook", routes)
        used = set()
        for name, d in self.dashboards.items():
            for panel, t in targets(d):
                for route in re.findall(r'http_route="([^"]+)"', t.get("expr", "")):
                    used.add(route)
                    self.assertIn(route, routes, f"{name}: '{panel['title']}' usa rota inexistente {route}")
        self.assertTrue(used)

    def test_target_info_is_not_filtered_by_service_name(self):
        for name, d in self.dashboards.items():
            for panel, t in targets(d):
                self.assertNotRegex(t.get("expr", ""), TARGET_INFO_BY_SERVICE, f"{name}: {panel['title']}")

    def test_links_point_to_existing_dashboards(self):
        uids = {d["uid"] for d in self.dashboards.values()}
        for name, d in self.dashboards.items():
            for link in d.get("links", []):
                m = re.match(r"^/d/([^/?]+)", link["url"])
                self.assertTrue(m and m.group(1) in uids, f"{name}: link {link['url']}")

    def test_compose_mounts_dashboards_and_home_exists(self):
        with open(os.path.join(STACK, "docker-compose.yml"), encoding="utf-8") as f:
            compose = f.read()
        self.assertIn("./dashboards:/var/lib/grafana/dashboards:ro", compose)
        self.assertIn("./grafana-dashboards.yaml:/etc/grafana/provisioning/dashboards/dashboards.yaml:ro", compose)
        home = re.search(r"GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH=/var/lib/grafana/dashboards/(\S+)", compose)
        self.assertTrue(home and home.group(1) in self.dashboards, "home dashboard inexistente")


class AlertRulesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.groups = load_alert_groups()
        cls.rules = list(alert_rules())
        cls.dashboards = {d["uid"]: d for d in load_dashboards().values()}

    def test_there_are_rules_and_uids_are_unique(self):
        self.assertGreaterEqual(len(self.rules), 10)
        uids = [r["uid"] for _, r in self.rules]
        self.assertEqual(len(uids), len(set(uids)), uids)
        for uid in uids:
            self.assertRegex(uid, r"^[a-zA-Z0-9_-]{1,40}$")
        names = [g["name"] for g in self.groups]
        self.assertEqual(len(names), len(set(names)))

    def test_alert_folder_differs_from_dashboard_folder(self):
        for g in self.groups:
            self.assertNotEqual(g["folder"], DASHBOARD_FOLDER, "pasta duplicada no Grafana (ver docstring)")
            self.assertRegex(g["interval"], r"^\d+[smh]$")

    def test_condition_and_queries_are_consistent(self):
        known = provisioned_datasource_uids() | {"__expr__"}
        for g, r in self.rules:
            refs = {d["refId"] for d in r["data"]}
            self.assertIn(r["condition"], refs, r["title"])
            for d in r["data"]:
                self.assertIn(d["datasourceUid"], known, r["title"])
                self.assertEqual(d["model"]["refId"], d["refId"], r["title"])
                if d["datasourceUid"] == "__expr__":
                    self.assertIn(d["model"]["expression"], refs, r["title"])
                else:
                    expr = d["model"]["expr"]
                    self.assertNotRegex(expr, TARGET_INFO_BY_SERVICE, r["title"])
                    self.assertIn('service_name="backend-spring"', expr, r["title"])

    def test_states_severity_and_duration(self):
        for g, r in self.rules:
            self.assertIn(r["noDataState"], {"OK", "NoData", "Alerting"}, r["title"])
            self.assertIn(r["execErrState"], {"OK", "Error", "Alerting"}, r["title"])
            self.assertRegex(r["for"], r"^\d+[smh]$", r["title"])
            self.assertIn(r["labels"]["severity"], {"critical", "warning"}, r["title"])
            self.assertFalse(r.get("isPaused"), r["title"])
        down = [r for _, r in self.rules if r["uid"] == "shopeasy-api-down"]
        self.assertTrue(down and down[0]["noDataState"] == "Alerting", "API fora do ar precisa disparar sem dados")

    def test_annotations_and_templates(self):
        for g, r in self.rules:
            a = r["annotations"]
            for key in ("summary", "description", "o_que_fazer"):
                self.assertTrue(a.get(key, "").strip(), f"{r['title']}: {key}")
            for text in a.values():
                for block in re.findall(r"{{(.*?)}}", text):
                    words = re.findall(r"(?:^|\()\s*([a-zA-Z_][a-zA-Z0-9_]*)", block)
                    for w in words:
                        self.assertIn(w, TEMPLATE_FUNCS, f"{r['title']}: função de template desconhecida '{w}'")
                    for ref in re.findall(r"\$values\.([A-Za-z0-9_]+)\.", block):
                        self.assertIn(ref, {d["refId"] for d in r["data"]}, r["title"])

    def test_dashboard_links_exist(self):
        for g, r in self.rules:
            a = r["annotations"]
            d = self.dashboards.get(a["__dashboardUid__"])
            self.assertIsNotNone(d, f"{r['title']}: dashboard {a['__dashboardUid__']}")
            self.assertIn(int(a["__panelId__"]), {p["id"] for p in d["panels"]}, r["title"])

    def test_routes_exist_in_controllers(self):
        routes = api_routes()
        for g, r in self.rules:
            for d in r["data"]:
                for route in re.findall(r'http_route="([^"]+)"', d["model"].get("expr", "")):
                    self.assertIn(route, routes, f"{r['title']}: rota inexistente {route}")

    def test_compose_mounts_alerting(self):
        with open(os.path.join(STACK, "docker-compose.yml"), encoding="utf-8") as f:
            self.assertIn("./alerting:/etc/grafana/provisioning/alerting:ro", f.read())


class RouteParserTest(unittest.TestCase):
    def test_parses_class_prefix_and_method_mappings(self):
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            with open(os.path.join(tmp, "X.java"), "w", encoding="utf-8") as f:
                f.write('@RestController\n@RequestMapping("/api/x")\npublic class X {\n'
                        '  @GetMapping\n  void a() {}\n'
                        '  @PostMapping("/{id}/go")\n  void b() {}\n'
                        '  @PostMapping(value = "/hook", consumes = "json")\n  void c() {}\n}\n')
            self.assertEqual(api_routes(tmp), {"/api/x", "/api/x/{id}/go", "/api/x/hook"})


if __name__ == "__main__":
    unittest.main()
