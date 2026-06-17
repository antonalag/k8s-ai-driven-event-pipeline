CHAOS_NS := chaos-validation
CHAOS_DIR := .

.PHONY: chaos-deploy chaos-clean chaos-status

chaos-deploy:
	@kubectl get ns $(CHAOS_NS) 2>/dev/null || kubectl create ns $(CHAOS_NS)
	@kubectl apply -f $(CHAOS_DIR)/crashloop-pod.yaml -n $(CHAOS_NS)
	@kubectl apply -f $(CHAOS_DIR)/oomkilled-pod.yaml -n $(CHAOS_NS)
	@kubectl apply -f $(CHAOS_DIR)/imagepull-pod.yaml -n $(CHAOS_NS)
	@echo "✅ Chaos scenarios deployed to namespace: $(CHAOS_NS)"

chaos-clean:
	@kubectl delete -f $(CHAOS_DIR)/crashloop-pod.yaml -n $(CHAOS_NS) --ignore-not-found 2>/dev/null || true
	@kubectl delete -f $(CHAOS_DIR)/oomkilled-pod.yaml -n $(CHAOS_NS) --ignore-not-found 2>/dev/null || true
	@kubectl delete -f $(CHAOS_DIR)/imagepull-pod.yaml -n $(CHAOS_NS) --ignore-not-found 2>/dev/null || true
	@echo "✅ Chaos resources cleaned from namespace: $(CHAOS_NS)"

chaos-status:
	@kubectl get pods -n $(CHAOS_NS) -l phase=e2e-validation -o wide
