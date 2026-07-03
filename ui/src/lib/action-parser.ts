import type { ParsedAction } from '../types/remediation';

/**
 * Parses a recommended action text into a typed ParsedAction for automated execution.
 * Returns null if the action text cannot be reliably parsed into a supported remediation.
 *
 * Supported patterns:
 * - kubectl rollout restart deployment/<name> [-n <namespace>]
 * - kubectl scale deployment/<name> --replicas=<N> [-n <namespace>]
 * - Image correction references with deployment and container context
 */

const RESTART_PATTERN =
  /kubectl\s+rollout\s+restart\s+deployment[/\s]+(\S+)(?:\s+(?:-n|--namespace)[=\s]+(\S+))?/i;

const SCALE_PATTERN =
  /kubectl\s+scale\s+deployment[/\s]+(\S+)\s+--replicas[=\s]+(\d+)(?:\s+(?:-n|--namespace)[=\s]+(\S+))?/i;

const FIX_IMAGE_PATTERN =
  /kubectl\s+set\s+image\s+deployment[/\s]+(\S+)\s+(\S+)=(\S+)(?:\s+(?:-n|--namespace)[=\s]+(\S+))?/i;

/**
 * Attempts to parse an action text into a structured ParsedAction.
 *
 * @param actionText - The raw recommended action string from AI analysis
 * @param defaultNamespace - Fallback namespace from the parent analysis context
 * @returns ParsedAction if parseable, null otherwise
 */
export function parseAction(actionText: string, defaultNamespace?: string): ParsedAction | null {
  // Try restart pattern first
  const restartMatch = actionText.match(RESTART_PATTERN);
  if (restartMatch) {
    return {
      action: 'restart_deployment',
      deploymentName: restartMatch[1],
      namespace: restartMatch[2] || defaultNamespace,
    };
  }

  // Try scale pattern
  const scaleMatch = actionText.match(SCALE_PATTERN);
  if (scaleMatch) {
    const replicas = parseInt(scaleMatch[2], 10);
    if (isNaN(replicas) || replicas < 0 || replicas > 10) {
      return null; // Out of safe range
    }
    return {
      action: 'scale_deployment',
      deploymentName: scaleMatch[1],
      namespace: scaleMatch[3] || defaultNamespace,
      replicas,
    };
  }

  // Try fix image pattern (kubectl set image)
  const fixImageMatch = actionText.match(FIX_IMAGE_PATTERN);
  if (fixImageMatch) {
    const deploymentName = fixImageMatch[1];
    let containerName = fixImageMatch[2];
    const correctImage = fixImageMatch[3];
    const namespace = fixImageMatch[4] || defaultNamespace;

    // Handle kubectl's "deployment.container=image" notation
    // e.g., "golden-path-app.app=nginx:latest" → containerName should be "app"
    if (containerName.includes('.')) {
      containerName = containerName.split('.').pop()!;
    }

    // When LLM uses deploymentName as containerName (common hallucination),
    // fall back to extracting the last segment after the last hyphen-separated
    // prefix that matches the deployment name pattern.
    // e.g., deployment="golden-path-app", containerName="golden-path-app" → use "app"
    if (containerName === deploymentName) {
      // Try to extract the last meaningful segment (e.g., "app" from "golden-path-app")
      const segments = containerName.split('-');
      containerName = segments[segments.length - 1];
    }

    return {
      action: 'fix_container_image',
      deploymentName,
      containerName,
      correctImage,
      namespace,
    };
  }

  return null;
}

/**
 * Returns a human-readable reason why a button should be disabled.
 * Returns null if the action is parseable and executable.
 */
export function getDisabledReason(actionText: string, defaultNamespace?: string): string | null {
  const parsed = parseAction(actionText, defaultNamespace);
  if (parsed === null) {
    return 'Cannot auto-parse this action';
  }

  if (parsed.action === 'scale_deployment' && parsed.replicas !== undefined) {
    if (parsed.replicas < 0 || parsed.replicas > 10) {
      return 'Replica count out of safe range (0-10)';
    }
  }

  if (!parsed.namespace && !defaultNamespace) {
    return 'Namespace could not be determined';
  }

  return null;
}
