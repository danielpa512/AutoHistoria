module.exports = { apps: [{ name: 'autohistoria-api', cwd: __dirname,
  script: 'server.mjs', node_args: '--env-file-if-exists=.env', instances: 1,
  exec_mode: 'fork', max_memory_restart: '512M', time: true,
  kill_timeout: 10000, env: { NODE_ENV: 'production' } }] };
