const backendTarget = process.env['LOQUELA_BACKEND_URL'] || 'http://127.0.0.1:8080';

module.exports = [
  {
    context: ['/api', '/actuator/health', '/oauth2', '/login/oauth2'],
    target: backendTarget,
    secure: false,
    changeOrigin: false,
    xfwd: true,
    logLevel: 'warn',
  },
];
