# Connecting Jira

The Jira tools come from Atlassian's MCP server at
`https://mcp.atlassian.com/v2/mcp`. Signing in uses OAuth in the browser, with
the Atlassian account that can see `naramnacakanyan77.atlassian.net`. There's
no API token to create or paste.

Only the person can do the sign-in step. Never ask them for their password or
a token, and never put one in a file.

## Claude Code CLI

1. Install Claude Code if `claude --version` doesn't work:

   ```bash
   curl -fsSL https://claude.ai/install.sh | bash
   ```

2. From the repository root, create your local MCP config from the example.
   `.mcp.json` is git-ignored, so this stays on your machine:

   ```bash
   cp .mcp.json.example .mcp.json
   ```

   Or add it for all your projects instead:

   ```bash
   claude mcp add --transport http --scope user atlassian https://mcp.atlassian.com/v2/mcp
   ```

3. Start `claude` in the repository. If it asks whether to trust the
   project's MCP servers, approve `atlassian`.
4. Type `/mcp`, select **atlassian**, choose **Authenticate**, and sign in to
   Atlassian in the browser. Choose the `naramnacakanyan77` site and accept.
5. Check it: ask "list open tickets in KAN". The answer should include
   tickets such as KAN-1 and KAN-2.

## MCP server, not a plugin or connector

This project connects Jira (and Qase) through the MCP servers in
`.mcp.json`, not through Claude Code plugins or claude.ai connectors. If
`/mcp` also lists a `claude.ai Atlassian` connector, or a plugin adds Jira
tools, disable it so there's one set of Jira tools: the `atlassian` server.
To keep claude.ai connectors out of the CLI entirely, start it with
`ENABLE_CLAUDEAI_MCP_SERVERS=false`.

## If it doesn't work

| Symptom | Fix |
|---|---|
| `/mcp` shows atlassian as "needs authentication" | Authenticate again from `/mcp`; tokens expire |
| Calls fail with "site not found" or return nothing | You signed in with an account or site that can't see KAN; sign in again and choose `naramnacakanyan77` |
| No `atlassian` entry in `/mcp` | `.mcp.json` is missing, or Claude was started outside the repository root |
