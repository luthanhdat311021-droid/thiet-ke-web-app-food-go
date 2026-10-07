/** @type {import('next').NextConfig} */
const nextConfig = {
  devIndicators: false,
  typescript: {
    ignoreBuildErrors: true,
  },
  images: {
    unoptimized: true,
  },
  // single-shop model: old per-restaurant links land on the menu
  async redirects() {
    return [{ source: '/restaurants/:id*', destination: '/menu', permanent: true }]
  },
}

export default nextConfig
