interface McpContextWarningProps {
  mcpContextAvailable: boolean;
}

export function McpContextWarning({ mcpContextAvailable }: McpContextWarningProps) {
  if (mcpContextAvailable) return null;

  return (
    <div
      className="flex items-center gap-2 px-3 py-2 bg-yellow-50 border border-yellow-200 rounded-md text-yellow-800 text-sm"
      role="alert"
      data-testid="mcp-context-warning"
    >
      <svg className="h-4 w-4 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20" aria-hidden="true">
        <path
          fillRule="evenodd"
          d="M8.485 2.495c.673-1.167 2.357-1.167 3.03 0l6.28 10.875c.673 1.167-.168 2.625-1.516 2.625H3.72c-1.347 0-2.189-1.458-1.515-2.625L8.485 2.495zM10 5a.75.75 0 01.75.75v3.5a.75.75 0 01-1.5 0v-3.5A.75.75 0 0110 5zm0 8a1 1 0 100-2 1 1 0 000 2z"
          clipRule="evenodd"
        />
      </svg>
      <span>
        Diagnostic produced without live cluster context — MCP circuit breaker was open or tools unavailable.
      </span>
    </div>
  );
}
