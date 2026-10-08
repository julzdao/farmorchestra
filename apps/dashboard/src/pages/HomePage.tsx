import { useState } from "react";
export function HomePage() {
  const [notice, setNotice] = useState("");
  return (
    <main className="home">
      <div className="home__content">
        <header className="brand">FarmOrchestra</header>
        <section>
          <p className="eyebrow">Grow. Observe. Learn.</p>
          <h1>Open source IoT dashboard for hydroponics.</h1>
          <p className="intro">
            Monitor your hydroponic system and learn to grow fresh, healthy food
            at home.
          </p>
          <a
            className="text-link"
            href="https://github.com/julzdao/farmorchestra"
          >
            Contribute on GitHub ↗
          </a>
        </section>
        <footer className="home__actions">
          <button
            className="button"
            onClick={() =>
              setNotice(
                "Login is a placeholder. Authentication is planned for a later version.",
              )
            }
          >
            Login
          </button>
          <button
            className="button button--dark"
            onClick={() =>
              setNotice(
                "Sign up is a placeholder. No accounts are created in this version.",
              )
            }
          >
            Sign up
          </button>
          <a className="button" href="#/dashboard">
            Open dashboard →
          </a>
        </footer>
        <p className="notice" role="status">
          {notice}
        </p>
      </div>
    </main>
  );
}
