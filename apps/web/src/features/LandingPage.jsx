import { Link } from 'react-router-dom';
import { useEffect, useState } from 'react';
import Icon from '../components/Icon';

export default function LandingPage() {
  const [navOpen, setNavOpen] = useState(false);

  useEffect(() => {
    const ro = new IntersectionObserver(
      entries => entries.forEach(e => {
        if (e.isIntersecting) { e.target.classList.add('lp-in'); ro.unobserve(e.target); }
      }),
      { threshold: 0.07, rootMargin: '0px 0px -40px 0px' }
    );
    document.querySelectorAll('[data-lp-r]').forEach(el => ro.observe(el));

    function countUp(el, end, suffix, dur = 1300) {
      const t0 = performance.now();
      (function tick() {
        const p = Math.min((performance.now() - t0) / dur, 1);
        const v = 1 - Math.pow(1 - p, 3);
        el.textContent = Math.round(v * end) + suffix;
        if (p < 1) requestAnimationFrame(tick);
      })();
    }
    const co = new IntersectionObserver(entries => {
      entries.forEach(e => {
        if (!e.isIntersecting) return;
        document.querySelectorAll('[data-lp-c]').forEach(el =>
          countUp(el, +el.dataset.lpC, el.dataset.lpS || '')
        );
        co.disconnect();
      });
    }, { threshold: 0.4 });
    const statsEl = document.querySelector('.lp-stats');
    if (statsEl) co.observe(statsEl);

    const onAnchorClick = e => {
      const a = e.target.closest('a[href^="#"]');
      if (!a) return;
      const target = document.querySelector(a.getAttribute('href'));
      if (target) { e.preventDefault(); target.scrollIntoView({ behavior: 'smooth', block: 'start' }); }
    };
    document.addEventListener('click', onAnchorClick);

    return () => { ro.disconnect(); co.disconnect(); document.removeEventListener('click', onAnchorClick); };
  }, []);

  // Lock body scroll when mobile nav is open
  useEffect(() => {
    document.body.style.overflow = navOpen ? 'hidden' : '';
    return () => { document.body.style.overflow = ''; };
  }, [navOpen]);

  return (
    <div className="lp">

      {/* NAV */}
      <nav className="lp-nav">
        <Link className="lp-nav-brand" to="/">
          <img src="/assets/logo-mark.svg" alt="LP" width="26" height="26" />
          <span className="lp-nav-logo">LearnPulse</span>
        </Link>
        <div className="lp-nav-links">
          <a className="lp-nav-link" href="#how">How it works</a>
          <a className="lp-nav-link" href="#feats">Why us</a>
          <a className="lp-nav-link" href="#courses">Courses</a>
          <a className="lp-nav-link" href="#inst">Instructors</a>
        </div>
        <div className="lp-nav-end">
          <Link className="lp-nav-sign" to="/login">Sign in</Link>
          <Link className="lp-btn-coral" to="/register">Start learning</Link>
          <button
            className="lp-ham"
            onClick={() => setNavOpen(o => !o)}
            aria-label={navOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={navOpen}
          >
            {navOpen
              ? <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              : <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="4" y1="8" x2="20" y2="8"/><line x1="4" y1="16" x2="20" y2="16"/></svg>
            }
          </button>
        </div>
      </nav>

      {/* MOBILE NAV MENU */}
      {navOpen && (
        <div className="lp-mob-menu" onClick={() => setNavOpen(false)}>
          <a className="lp-mob-link" href="#how" onClick={() => setNavOpen(false)}>How it works</a>
          <a className="lp-mob-link" href="#feats" onClick={() => setNavOpen(false)}>Why us</a>
          <a className="lp-mob-link" href="#courses" onClick={() => setNavOpen(false)}>Courses</a>
          <a className="lp-mob-link" href="#inst" onClick={() => setNavOpen(false)}>Instructors</a>
          <div className="lp-mob-ctas">
            <Link className="lp-btn-ghost-w" to="/login" style={{ justifyContent: 'center' }} onClick={() => setNavOpen(false)}>Sign in</Link>
            <Link className="lp-btn-coral lp-btn-coral-lg" to="/register" style={{ justifyContent: 'center' }} onClick={() => setNavOpen(false)}>Start learning →</Link>
          </div>
        </div>
      )}

      {/* HERO */}
      <section className="lp-hero">
        <div className="lp-h-glow" />
        <div className="lp-h-grid" />
        <div className="lp-hero-body">
          <h1 className="lp-hero-h1">
            <span className="lp-hl lp-hl-1">Quietly confident</span>
            <span className="lp-hl lp-hl-2"><em className="lp-hl-coral">learning.</em></span>
          </h1>
          <p className="lp-hero-lede">
            Short, well-paced courses taught by people who've done the work. Learn at your pace — earn a permanent certificate the moment you finish.
          </p>
          <div className="lp-hero-ctas">
            <Link className="lp-btn-coral lp-btn-coral-lg" to="/register">Browse the catalogue →</Link>
            <a className="lp-btn-ghost-w" href="#how">How it works</a>
          </div>
          <div className="lp-hero-stats">
            <div className="lp-hs"><span className="lp-hs-n">140+</span><span className="lp-hs-l">courses</span></div>
            <div className="lp-hs"><span className="lp-hs-n">72k+</span><span className="lp-hs-l">learners this year</span></div>
            <div className="lp-hs"><span className="lp-hs-n">11k+</span><span className="lp-hs-l">certificates issued</span></div>
            <div className="lp-hs"><span className="lp-hs-n">4.9 ★</span><span className="lp-hs-l">average rating</span></div>
          </div>
        </div>

        {/* App mockup */}
        <div className="lp-mockup-outer">
          <div className="lp-m-stage">
            <div className="lp-m-frame">
              <div className="lp-m-chrome">
                <div className="lp-m-dots">
                  <div className="lp-m-dot r" />
                  <div className="lp-m-dot y" />
                  <div className="lp-m-dot g" />
                </div>
                <div className="lp-m-urlbar">
                  <div className="lp-m-url">🔒 learnpulse.io/dashboard</div>
                </div>
                <div style={{ width: 80 }} />
              </div>
              <div className="lp-m-app">
                <aside className="lp-m-side">
                  <div className="lp-m-brand">
                    <img src="/assets/logo-mark.svg" width="15" alt="" />
                    <span className="lp-m-brand-n">LearnPulse</span>
                  </div>
                  <div className="lp-mn a">
                    <svg width="13" height="13" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
                      <rect x="2" y="2" width="5" height="5" rx="1"/><rect x="9" y="2" width="5" height="5" rx="1"/>
                      <rect x="2" y="9" width="5" height="5" rx="1"/><rect x="9" y="9" width="5" height="5" rx="1"/>
                    </svg>
                    Dashboard
                  </div>
                  <div className="lp-mn">
                    <svg width="13" height="13" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
                      <path d="M3 4h10M3 8h7M3 12h5"/>
                    </svg>
                    Catalogue
                  </div>
                  <div className="lp-mn">
                    <svg width="13" height="13" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
                      <circle cx="8" cy="8" r="6"/><path d="M6 5.5l5 2.5-5 2.5V5.5z" fill="currentColor" stroke="none"/>
                    </svg>
                    Continue
                  </div>
                  <div className="lp-mn">
                    <svg width="13" height="13" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
                      <path d="M8 2l1.8 3.6L14 6.4l-3 2.9.7 4.1L8 11.4l-3.7 1.9.7-4.1L2 6.4l4.2-.8L8 2z"/>
                    </svg>
                    Certificates
                  </div>
                  <div className="lp-m-sep" />
                  <div className="lp-mn">
                    <svg width="13" height="13" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
                      <circle cx="8" cy="5.5" r="2.5"/><path d="M2 14c0-3.3 2.7-6 6-6s6 2.7 6 6"/>
                    </svg>
                    Settings
                  </div>
                  <div className="lp-m-u">
                    <div className="lp-m-av">AR</div>
                    <div>
                      <div className="lp-m-un">Alex Reyes</div>
                      <div className="lp-m-ur">Learner</div>
                    </div>
                  </div>
                </aside>
                <main className="lp-m-main">
                  <div className="lp-m-top">
                    <div className="lp-m-srch">
                      <svg width="11" height="11" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
                        <circle cx="7" cy="7" r="5"/><path d="M11 11l3 3"/>
                      </svg>
                      Search courses, instructors…
                    </div>
                    <div className="lp-m-ic">
                      <svg width="13" height="13" viewBox="0 0 16 16" fill="none" stroke="rgba(251,248,243,.5)" strokeWidth="1.5">
                        <path d="M8 2c-2.2 0-4 1.8-4 4 0 2.5-1 3-1 4h10c0-1-.8-1.5-1-4 0-2.2-1.8-4-4-4z"/>
                      </svg>
                    </div>
                  </div>
                  <div className="lp-m-pt">Pick up where you left off, Alex.</div>
                  <div className="lp-m-ps">18 minutes left in Data structures, in plain English. Module 3 unlocks when you finish module 2.</div>
                  <div className="lp-m-acts">
                    <div className="lp-m-bp">▶ Continue lesson</div>
                    <div className="lp-m-bs">Browse catalogue</div>
                  </div>
                  <div className="lp-m-st">In progress</div>
                  <div className="lp-m-cards">
                    <div className="lp-m-card">
                      <div className="lp-m-th cs" />
                      <div className="lp-m-ct">CS · Intermediate</div>
                      <div className="lp-m-cn">Data structures, in plain English</div>
                      <div className="lp-m-pb"><div className="lp-m-pf" style={{ width: '33%', background: 'var(--coral)' }} /></div>
                      <div className="lp-m-pm"><span>4/12 lessons</span><span>18 min left</span></div>
                    </div>
                    <div className="lp-m-card">
                      <div className="lp-m-th wr" />
                      <div className="lp-m-ct">Writing · Beginner</div>
                      <div className="lp-m-cn">Plain language at work</div>
                      <div className="lp-m-pb"><div className="lp-m-pf" style={{ width: '12%', background: 'var(--coral)' }} /></div>
                      <div className="lp-m-pm"><span>1/9 lessons</span><span>72 min left</span></div>
                    </div>
                    <div className="lp-m-card">
                      <div className="lp-m-th dz" />
                      <div className="lp-m-ct">Design · Beginner</div>
                      <div className="lp-m-cn">A quiet course on layout</div>
                      <div className="lp-m-pb"><div className="lp-m-pf" style={{ width: '100%', background: '#2F7A4D' }} /></div>
                      <div className="lp-m-pm"><span style={{ color: '#86c9a4' }}>Complete ✦</span><span>8/8</span></div>
                    </div>
                  </div>
                </main>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* STATS */}
      <section className="lp-stats">
        <div className="lp-stats-g">
          <div className="lp-sc" data-lp-r>
            <span className="lp-sc-n" data-lp-c="140" data-lp-s="+">140+</span>
            <span className="lp-sc-l">courses across 12 topics</span>
          </div>
          <div className="lp-sc" data-lp-r style={{ '--d': 1 }}>
            <span className="lp-sc-n" data-lp-c="72" data-lp-s="k+">72k+</span>
            <span className="lp-sc-l">learners enrolled this year</span>
          </div>
          <div className="lp-sc" data-lp-r style={{ '--d': 2 }}>
            <span className="lp-sc-n" data-lp-c="11" data-lp-s="k+">11k+</span>
            <span className="lp-sc-l">certificates issued</span>
          </div>
          <div className="lp-sc" data-lp-r style={{ '--d': 3 }}>
            <span className="lp-sc-n" data-lp-c="38" data-lp-s="">38</span>
            <span className="lp-sc-l">expert instructors</span>
          </div>
        </div>
      </section>

      {/* HOW IT WORKS */}
      <section id="how" className="lp-how">
        <div className="lp-how-in">
          <h2 className="lp-how-h2" data-lp-r>
            Three steps<br />from here to certified.
          </h2>
          <div className="lp-how-grid">
            <div className="lp-hw" data-lp-r style={{ '--d': 1 }}>
              <div className="lp-hw-num">01</div>
              <div className="lp-hw-ico"><Icon name="compass" size={22} /></div>
              <h3 className="lp-hw-h">Pick a course that fits you.</h3>
              <p className="lp-hw-p">Browse 140+ courses across CS, design, writing, and more. No algorithm nudging you — just a library.</p>
            </div>
            <div className="lp-hw" data-lp-r style={{ '--d': 2 }}>
              <div className="lp-hw-num">02</div>
              <div className="lp-hw-ico"><Icon name="play-circle" size={22} /></div>
              <h3 className="lp-hw-h">Learn in short, focused bursts.</h3>
              <p className="lp-hw-p">Five to ten minutes per lesson. Modules unlock as you go. Stop mid-lesson and pick up exactly where you left off.</p>
            </div>
            <div className="lp-hw" data-lp-r style={{ '--d': 3 }}>
              <div className="lp-hw-num">03</div>
              <div className="lp-hw-ico"><Icon name="award" size={22} /></div>
              <h3 className="lp-hw-h">Earn your certificate.</h3>
              <p className="lp-hw-p">Finish the last lesson and your certificate generates instantly. One email, one permanent link. No chasing anyone.</p>
            </div>
          </div>
        </div>
      </section>

      {/* FEATURES */}
      <section id="feats" className="lp-feats">
        <div className="lp-feats-in">
          <h2 className="lp-feats-h2" data-lp-r>
            Built different.<br />Because we had to be.
          </h2>
          <div className="lp-feats-grid">
            <div className="lp-fc" data-lp-r style={{ '--d': 1 }}>
              <div className="lp-fc-ico">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
                </svg>
              </div>
              <h3 className="lp-fc-h">Short lessons, zero bloat</h3>
              <p className="lp-fc-p">Every lesson is five to ten minutes. No drawn-out intros, no filler. The ideas are there — the time-wasting is not.</p>
            </div>
            <div className="lp-fc" data-lp-r style={{ '--d': 2 }}>
              <div className="lp-fc-ico"><Icon name="lock" size={22} /></div>
              <h3 className="lp-fc-h">Modules that unlock as you go</h3>
              <p className="lp-fc-p">Finish one module and the next one opens. A structure that gives you direction without the overwhelm of seeing everything at once.</p>
            </div>
            <div className="lp-fc" data-lp-r style={{ '--d': 3 }}>
              <div className="lp-fc-ico"><Icon name="award" size={22} /></div>
              <h3 className="lp-fc-h">Your certificate, instantly</h3>
              <p className="lp-fc-p">Generated the moment you finish. Emailed once. Permanently linked. Share it on LinkedIn or anywhere you like.</p>
            </div>
            <div className="lp-fc" data-lp-r style={{ '--d': 4 }}>
              <div className="lp-fc-ico"><Icon name="sparkles" size={22} /></div>
              <h3 className="lp-fc-h">AI study assistant, built in</h3>
              <p className="lp-fc-p">Ask anything about the course you're taking. Answers come from your exact lesson content with citations, so you can verify.</p>
            </div>
          </div>
        </div>
      </section>

      {/* COURSES */}
      <section id="courses" className="lp-courses">
        <div className="lp-courses-in">
          <div className="lp-courses-hd" data-lp-r>
            <h2 className="lp-courses-h2">Start somewhere small.</h2>
            <Link className="lp-btn-outline" to="/register">All 140 courses →</Link>
          </div>
          <div className="lp-cg">
            <div className="lp-cc" data-lp-r style={{ '--d': 1 }}>
              <div className="lp-cc-th cs">
                <span className="lp-cc-badge int">Intermediate</span>
                <span className="lp-cc-dur">~ 2 hrs</span>
              </div>
              <div className="lp-cc-bd">
                <div className="lp-cc-top">
                  <div className="lp-cc-tag">Computer science</div>
                  <div className="lp-cc-rating">★ 4.9</div>
                </div>
                <h3 className="lp-cc-h">Data structures, in plain English</h3>
                <p className="lp-cc-p">Twelve evenings, no jargon. Lists, trees, hash maps — and when to reach for each one.</p>
                <div className="lp-cc-foot">
                  <div className="lp-cc-inst">
                    <div className="lp-cc-av mo">MO</div>
                    <div>
                      <span className="lp-cc-iname">Marta Olsen</span>
                      <span className="lp-cc-imeta">12 lessons</span>
                    </div>
                  </div>
                  <span className="lp-cc-enroll">624 enrolled</span>
                </div>
              </div>
            </div>

            <div className="lp-cc" data-lp-r style={{ '--d': 2 }}>
              <div className="lp-cc-th dz">
                <span className="lp-cc-badge beg">Beginner</span>
                <span className="lp-cc-dur">~ 90 min</span>
              </div>
              <div className="lp-cc-bd">
                <div className="lp-cc-top">
                  <div className="lp-cc-tag">Design</div>
                  <div className="lp-cc-rating">★ 4.8</div>
                </div>
                <h3 className="lp-cc-h">A quiet course on layout</h3>
                <p className="lp-cc-p">Grids, alignment, type. The bones of every good interface, explained without gatekeeping.</p>
                <div className="lp-cc-foot">
                  <div className="lp-cc-inst">
                    <div className="lp-cc-av ip">IP</div>
                    <div>
                      <span className="lp-cc-iname">Iona Park</span>
                      <span className="lp-cc-imeta">8 lessons</span>
                    </div>
                  </div>
                  <span className="lp-cc-enroll">418 enrolled</span>
                </div>
              </div>
            </div>

            <div className="lp-cc" data-lp-r style={{ '--d': 3 }}>
              <div className="lp-cc-th wr">
                <span className="lp-cc-badge beg">Beginner</span>
                <span className="lp-cc-dur">~ 75 min</span>
              </div>
              <div className="lp-cc-bd">
                <div className="lp-cc-top">
                  <div className="lp-cc-tag">Writing</div>
                  <div className="lp-cc-rating">★ 4.7</div>
                </div>
                <h3 className="lp-cc-h">Plain language at work</h3>
                <p className="lp-cc-p">Write emails people actually finish reading. Short sentences, active verbs, no corporate fuzz.</p>
                <div className="lp-cc-foot">
                  <div className="lp-cc-inst">
                    <div className="lp-cc-av tr">TR</div>
                    <div>
                      <span className="lp-cc-iname">Tomás Reyes</span>
                      <span className="lp-cc-imeta">9 lessons</span>
                    </div>
                  </div>
                  <span className="lp-cc-enroll">242 enrolled</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* QUOTE */}
      <section className="lp-quote">
        <div className="lp-q-in">
          <span className="lp-q-mk" data-lp-r>&ldquo;</span>
          <p className="lp-q-tx" data-lp-r style={{ '--d': 1 }}>
            I finished a course on the train, in twenty-minute pieces. The certificate was waiting in my inbox before I got home.
          </p>
          <div className="lp-q-by" data-lp-r style={{ '--d': 2 }}>
            <strong>Alex Reyes</strong> &nbsp;·&nbsp; finished <em>A quiet course on layout</em>
          </div>
        </div>
      </section>

      {/* INSTRUCTORS */}
      <section id="inst" className="lp-inst">
        <div className="lp-inst-in">
          <h2 className="lp-inst-h2" data-lp-r>
            The people<br />behind the courses.
          </h2>
          <div className="lp-inst-g">
            <div className="lp-ic" data-lp-r style={{ '--d': 1 }}>
              <div className="lp-ic-av a">MO</div>
              <div className="lp-ic-n">Marta Olsen</div>
              <div className="lp-ic-r">Principal engineer · 12 years</div>
              <p className="lp-ic-b">Former lead at a fintech company with a decade building distributed systems. Teaches CS the way she wished it had been explained to her.</p>
              <div className="lp-ic-s">3 courses · 1,284 learners</div>
            </div>
            <div className="lp-ic" data-lp-r style={{ '--d': 2 }}>
              <div className="lp-ic-av b">IP</div>
              <div className="lp-ic-n">Iona Park</div>
              <div className="lp-ic-r">Senior product designer · 9 years</div>
              <p className="lp-ic-b">Design lead at a product studio. Teaches layout and type the way she learned — by making hundreds of mistakes first.</p>
              <div className="lp-ic-s">2 courses · 418 learners</div>
            </div>
            <div className="lp-ic" data-lp-r style={{ '--d': 3 }}>
              <div className="lp-ic-av c">TR</div>
              <div className="lp-ic-n">Tomás Reyes</div>
              <div className="lp-ic-r">Head of communications · 8 years</div>
              <p className="lp-ic-b">Started as a journalist, ended up running comms for 400 people. Writes like he talks — which turns out to be exactly what people need.</p>
              <div className="lp-ic-s">1 course · 242 learners</div>
            </div>
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="lp-cta">
        <div className="lp-cta-in">
          <h2 className="lp-cta-h2" data-lp-r>
            Pick something.<br /><em>Start tonight.</em>
          </h2>
          <p className="lp-cta-sub" data-lp-r style={{ '--d': 1 }}>
            Free to browse. Most courses cost less than a paperback. Your first certificate is worth more than both.
          </p>
          <div data-lp-r style={{ '--d': 2 }}>
            <Link className="lp-btn-coral lp-btn-coral-lg" to="/register">Browse the catalogue →</Link>
          </div>
        </div>
      </section>

      {/* FOOTER */}
      <footer className="lp-foot">
        <div className="lp-foot-in">
          <div>
            <img src="/assets/logo-mark.svg" width="26" height="26" alt="LearnPulse" style={{ opacity: 0.7 }} />
            <span className="lp-ft">Quietly confident learning.</span>
          </div>
          <div>
            <div className="lp-fch">Learn</div>
            <a className="lp-fa" href="#">Catalogue</a>
            <a className="lp-fa" href="#">Topics</a>
            <a className="lp-fa" href="#">Instructors</a>
            <a className="lp-fa" href="#">For teams</a>
          </div>
          <div>
            <div className="lp-fch">Company</div>
            <a className="lp-fa" href="#">About</a>
            <a className="lp-fa" href="#">Blog</a>
            <a className="lp-fa" href="#">Careers</a>
            <a className="lp-fa" href="#">Press</a>
          </div>
          <div>
            <div className="lp-fch">Help</div>
            <a className="lp-fa" href="#">FAQ</a>
            <a className="lp-fa" href="#">Contact</a>
            <a className="lp-fa" href="#">Accessibility</a>
            <a className="lp-fa" href="#">Status</a>
          </div>
        </div>
        <div className="lp-fb">
          <span>© 2026 LearnPulse, Inc.</span>
          <span className="lp-fb-dev">Developed by John Agene &amp; Anthony Alikah</span>
          <span>Privacy · Terms · Cookies</span>
        </div>
      </footer>

    </div>
  );
}