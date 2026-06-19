/**
 * Property-based and example-based tests for the action-parser utility.
 * Validates: Requirements 12.1-12.4 (action text parsing & request mapping)
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { parseAction, getDisabledReason } from './action-parser';

// --- Generators ---

const deploymentNameArb = fc.stringMatching(/^[a-z][a-z0-9\-]{0,30}[a-z0-9]$/).filter(s => s.length >= 2);
const namespaceArb = fc.stringMatching(/^[a-z][a-z0-9\-]{0,20}[a-z0-9]$/).filter(s => s.length >= 2);
const replicasArb = fc.integer({ min: 0, max: 10 });
const containerNameArb = fc.stringMatching(/^[a-z][a-z0-9\-]{0,15}$/).filter(s => s.length >= 2);
const imageArb = fc.constantFrom(
  'nginx:1.27-alpine',
  'gcr.io/my-project/app:v2.1.0',
  'redis:7.2',
  'envoyproxy/envoy:v1.30',
);

// --- Property Tests ---

describe('parseAction — property-based', () => {
  it('correctly parses any valid kubectl rollout restart command', () => {
    fc.assert(
      fc.property(deploymentNameArb, namespaceArb, (deployment, ns) => {
        const text = `kubectl rollout restart deployment/${deployment} -n ${ns}`;
        const result = parseAction(text);

        expect(result).not.toBeNull();
        expect(result!.action).toBe('restart_deployment');
        expect(result!.deploymentName).toBe(deployment);
        expect(result!.namespace).toBe(ns);
      }),
      { numRuns: 50 },
    );
  });

  it('correctly parses any valid kubectl scale command with safe replicas', () => {
    fc.assert(
      fc.property(deploymentNameArb, replicasArb, namespaceArb, (deployment, replicas, ns) => {
        const text = `kubectl scale deployment/${deployment} --replicas=${replicas} -n ${ns}`;
        const result = parseAction(text);

        expect(result).not.toBeNull();
        expect(result!.action).toBe('scale_deployment');
        expect(result!.deploymentName).toBe(deployment);
        expect(result!.replicas).toBe(replicas);
        expect(result!.namespace).toBe(ns);
      }),
      { numRuns: 50 },
    );
  });

  it('correctly parses any valid kubectl set image command', () => {
    fc.assert(
      fc.property(deploymentNameArb, containerNameArb, imageArb, namespaceArb, (deployment, container, image, ns) => {
        const text = `kubectl set image deployment/${deployment} ${container}=${image} -n ${ns}`;
        const result = parseAction(text);

        expect(result).not.toBeNull();
        expect(result!.action).toBe('fix_container_image');
        expect(result!.deploymentName).toBe(deployment);
        expect(result!.containerName).toBe(container);
        expect(result!.correctImage).toBe(image);
        expect(result!.namespace).toBe(ns);
      }),
      { numRuns: 50 },
    );
  });

  it('returns null for non-kubectl action text', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 5, maxLength: 100 }).filter(s => !s.includes('kubectl')),
        (text) => {
          const result = parseAction(text);
          expect(result).toBeNull();
        },
      ),
      { numRuns: 50 },
    );
  });

  it('rejects scale commands with replicas > 10', () => {
    fc.assert(
      fc.property(
        deploymentNameArb,
        fc.integer({ min: 11, max: 999 }),
        (deployment, replicas) => {
          const text = `kubectl scale deployment/${deployment} --replicas=${replicas}`;
          const result = parseAction(text);
          expect(result).toBeNull();
        },
      ),
      { numRuns: 20 },
    );
  });
});

// --- Example-based Tests ---

describe('parseAction — examples', () => {
  it('parses restart without -n flag using defaultNamespace', () => {
    const result = parseAction('kubectl rollout restart deployment/my-app', 'production');
    expect(result).toEqual({
      action: 'restart_deployment',
      deploymentName: 'my-app',
      namespace: 'production',
    });
  });

  it('parses scale without -n flag using defaultNamespace', () => {
    const result = parseAction('kubectl scale deployment/web --replicas=3', 'staging');
    expect(result).toEqual({
      action: 'scale_deployment',
      deploymentName: 'web',
      namespace: 'staging',
      replicas: 3,
    });
  });

  it('parses fix image without -n flag using defaultNamespace', () => {
    const result = parseAction('kubectl set image deployment/api main=nginx:1.27', 'default');
    expect(result).toEqual({
      action: 'fix_container_image',
      deploymentName: 'api',
      containerName: 'main',
      correctImage: 'nginx:1.27',
      namespace: 'default',
    });
  });

  it('returns null for descriptive text actions', () => {
    expect(parseAction('Check pod logs for more details')).toBeNull();
    expect(parseAction('Increase memory limits in deployment manifest')).toBeNull();
    expect(parseAction('Review HPA configuration')).toBeNull();
  });

  it('returns null for incomplete kubectl commands', () => {
    expect(parseAction('kubectl get pods')).toBeNull();
    expect(parseAction('kubectl describe pod/my-pod')).toBeNull();
  });
});

// --- getDisabledReason Tests ---

describe('getDisabledReason', () => {
  it('returns null for parseable actions with namespace', () => {
    expect(getDisabledReason('kubectl rollout restart deployment/app -n default')).toBeNull();
    expect(getDisabledReason('kubectl scale deployment/app --replicas=3 -n prod')).toBeNull();
  });

  it('returns null for parseable actions with defaultNamespace', () => {
    expect(getDisabledReason('kubectl rollout restart deployment/app', 'default')).toBeNull();
  });

  it('returns reason for unparseable actions', () => {
    expect(getDisabledReason('check pod logs')).toBe('Cannot auto-parse this action');
  });

  it('returns reason when namespace cannot be determined', () => {
    expect(getDisabledReason('kubectl rollout restart deployment/app')).toBe(
      'Namespace could not be determined',
    );
  });
});
