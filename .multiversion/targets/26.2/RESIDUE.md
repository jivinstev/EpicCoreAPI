# Residue: gates

Stopped at **gates**: 1 gate(s) red: author's own build (`build agentJar cfJar devJar jvmtiBackendJar mrJar proAgentJar proJar`.

## Failing gates

- author's own build (`build agentJar cfJar devJar jvmtiBackendJar mrJar proAgentJar proJar`): FAIL -- epic-core-api-1.1.9_cf-mc26.2.jar, epic-core-api-1.1.9_dev-mc26.2.jar, epic-core-api-1.1.9_mr-mc26.

## To resume

`python3 tools/fork-port.py --repo /home/user/eca-layer --target 26.2 --from-stage gates --budget 5.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
