/* eslint-env node */
module.exports = {
  root: true,
  env: { browser: true, es2022: true },
  extends: [
    'eslint:recommended',
    'plugin:react/recommended',
    'plugin:react/jsx-runtime',
    'plugin:react-hooks/recommended',
  ],
  ignorePatterns: ['dist', 'coverage', 'node_modules'],
  parserOptions: { ecmaVersion: 'latest', sourceType: 'module' },
  settings: { react: { version: 'detect' } },
  plugins: ['react-refresh'],
  rules: {
    'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    'no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    'no-console': ['warn', { allow: ['warn', 'error'] }],
    // Off deliberately. prop-types is a runtime type checker for a codebase that has
    // no other runtime type checking, and it drifts from the component the moment
    // anybody edits one without the other. Component contracts are documented in
    // JSDoc above each component instead, where they cost nothing at runtime.
    'react/prop-types': 'off',
  },
  overrides: [
    {
      files: ['**/*.{test,spec}.{js,jsx}', 'src/test/**'],
      env: { node: true },
      globals: { vi: 'readonly', describe: 'readonly', it: 'readonly', expect: 'readonly', beforeEach: 'readonly' },
    },
    {
      files: ['vite.config.js', 'tailwind.config.js', 'postcss.config.js'],
      env: { node: true },
    },
  ],
}
