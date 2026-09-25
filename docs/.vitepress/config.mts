import { defineConfig } from "vitepress";

export default defineConfig({
  title: "Cana",
  base: "/cana/",
  description: "Do more. Because you can.",
  head: [
    ["meta", { name: "author", content: "Cana contributors; based on Canta by samolego" }],
    [
      "meta",
      {
        name: "keywords",
        content: "cana, android, profiles, uninstall, debloat, shizuku",
      },
    ],
    ["meta", { property: "og:type", content: "website" }],
    [
      "meta",
      {
        property: "og:title",
        content: "Cana - Do more. Because you can.",
      },
    ],
    [
      "meta",
      {
        property: "og:image",
        content:
          "https://jordanwoodson.github.io/cana/branding/cana-icon.png",
      },
    ],
    [
      "meta",
      { property: "og:url", content: "https://jordanwoodson.github.io/cana" },
    ],
    [
      "meta",
      {
        property: "og:description",
        content: "More control over your Android apps. Debloat personal, work, and other user profiles with Shizuku.",
      },
    ],
    ["meta", { name: "twitter:card", content: "summary" }],
    // Favicon
    [
      "link",
      {
        rel: "icon",
        href: "/cana/branding/cana-icon.svg",
      },
    ],
  ],
  sitemap: {
    hostname: "https://jordanwoodson.github.io/cana/",
  },
  lastUpdated: true,
  // Theme customization
  themeConfig: {
    logo: { src: "/branding/cana-icon.svg", alt: "Cana Open C icon" },
    siteTitle: "(can)a",
    nav: [
      { text: "Home", link: "/" },
      { text: "Install", link: "/install" },
      { text: "Features", link: "/features" },
      { text: "Presets", link: "/presets" },
      { text: "Download", link: "/download" },
    ],

    search: {
      provider: "local",
    },

    editLink: {
      pattern: "https://github.com/jordanwoodson/cana/edit/master/docs/:path",
      text: "Edit this page on GitHub",
    },

    sidebar: [
      {
        text: "Getting Started",
        items: [
          { text: "Setup", link: "/install" },
          { text: "Usage", link: "/usage" },
          { text: "Settings", link: "/settings" },
        ],
      },
      {
        text: "Advanced Features",
        items: [
          { text: "Features", link: "/features" },
          { text: "Presets", link: "/presets" },
        ],
      },
    ],

    socialLinks: [
      { icon: "github", link: "https://github.com/jordanwoodson/cana" },
    ],

    footer: {
      message: "Released under the LGPL-3.0 License.",
      copyright: 'Cana contributors · Based on <a href="https://github.com/samolego/Canta">Canta</a> by samolego',
    },
  },

  // CSS customization
  appearance: "dark",

});
