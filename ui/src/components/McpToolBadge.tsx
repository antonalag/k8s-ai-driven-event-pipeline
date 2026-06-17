interface McpToolBadgeProps {
  mcpToolsUsed?: string[];
}

const TOOL_COLORS: Record<string, string> = {
  describe_pod: 'bg-blue-100 text-blue-800',
  get_events: 'bg-purple-100 text-purple-800',
  get_logs: 'bg-amber-100 text-amber-800',
};

export function McpToolBadge({ mcpToolsUsed }: McpToolBadgeProps) {
  if (!mcpToolsUsed || mcpToolsUsed.length === 0) return null;

  return (
    <div className="flex gap-2 flex-wrap" data-testid="mcp-tool-badges">
      {mcpToolsUsed.map((tool) => (
        <span
          key={tool}
          className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${TOOL_COLORS[tool] || 'bg-gray-100 text-gray-800'}`}
          data-testid={`mcp-badge-${tool}`}
        >
          {tool}
        </span>
      ))}
    </div>
  );
}
