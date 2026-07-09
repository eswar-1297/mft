/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // CloudFuze brand
        brand: {
          DEFAULT: '#0129AC',
          header: '#0029AB',
          dark: '#001F80',
        },
        ink: '#1A1F36',
        muted: '#7A8BA6',
        lightblue: '#DBEAFE',
        nearwhite: '#F7F8FC',
        danger: '#DC2626',
        success: '#16A34A',
        warning: '#D97706',
      },
      fontFamily: {
        sans: ['Poppins', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        card: '0 1px 3px rgba(16,24,54,0.06), 0 1px 2px rgba(16,24,54,0.04)',
        cardhover: '0 4px 16px rgba(16,24,54,0.10)',
      },
    },
  },
  plugins: [],
}
