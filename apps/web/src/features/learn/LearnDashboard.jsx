import { useNavigate } from 'react-router-dom';
import Icon from '../../components/Icon';
import ProgressBar from '../../components/ProgressBar';
import Tag from '../../components/Tag';
import { COURSES } from '../../data/mockData';
import useAuthStore from '../../store/authStore';

export default function LearnDashboard() {
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const firstName = user?.firstName ?? 'there';

  const inProgress = COURSES.filter(c => c.progress > 0 && c.progress < 100);
  const completed  = COURSES.filter(c => c.progress === 100);
  const notStarted = COURSES.filter(c => c.progress === 0 && c.status === 'published');
  const continueCourse = inProgress[0];

  return (
    <div className="main">
      <div className="page-eyebrow">Tuesday, 6 May 2026</div>
      <h1 className="page-title">Pick up where you left off, {firstName}.</h1>
      <p className="page-lede">
        {continueCourse
          ? `About ${continueCourse.minsLeft} minutes left in ${continueCourse.title}. Module 3 unlocks when you finish module 2.`
          : 'Browse the catalogue to find your next course.'}
      </p>
      <div style={{ display: 'flex', gap: 10, marginBottom: 36 }}>
        <button className="btn btn-primary" onClick={() => navigate('/learn/play')}>
          Continue lesson <Icon name="play" size={15} />
        </button>
        <button className="btn btn-secondary" onClick={() => navigate('/learn/browse')}>Browse catalogue</button>
      </div>

      <h2 className="section-head">Continue learning</h2>
      <div className="course-grid">
        {inProgress.map(c => (
          <div key={c.id} className="course-card" onClick={() => navigate('/learn/play')}>
            <div className="thumb"><Icon name="book-open" size={32} className="thumb-icon" /></div>
            <div className="card-eyebrow">{c.topic} · {c.level}</div>
            <h3>{c.title}</h3>
            <div className="card-meta">by {c.instructor}</div>
            <div className="card-footer">
              <ProgressBar value={c.progress} />
              <div className="card-stats">
                <span>{c.lessonsDone} of {c.lessons} lessons</span>
                <span>~ {c.minsLeft} min left</span>
              </div>
            </div>
          </div>
        ))}
      </div>

      <h2 className="section-head">Explore courses</h2>
      <div className="course-grid">
        {notStarted.map(c => (
          <div key={c.id} className="course-card" onClick={() => navigate(`/learn/courses/${c.id}`)}>
            <div className="thumb"><Icon name="book-open" size={32} className="thumb-icon" /></div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span className="card-eyebrow">{c.topic} · {c.level}</span>
              {c.visibility === 'private' && <Tag variant="private">Private 🔒</Tag>}
            </div>
            <h3>{c.title}</h3>
            <div className="card-meta">by {c.instructor} · {c.modules} modules · {c.lessons} lessons</div>
            <div className="card-footer">
              <button className="btn btn-secondary btn-sm" style={{ alignSelf: 'flex-start' }}>
                {c.visibility === 'private' ? 'Request access' : 'Enrol'}
              </button>
            </div>
          </div>
        ))}
      </div>

      {completed.length > 0 && (
        <>
          <h2 className="section-head">Completed</h2>
          <div className="course-grid">
            {completed.map(c => (
              <div key={c.id} className="course-card" onClick={() => navigate('/learn/certificates')}>
                <div className="thumb" style={{ backgroundImage: 'radial-gradient(at 100% 0%, rgba(47,122,77,.5), transparent 60%), radial-gradient(at 0% 100%, rgba(42,45,124,.6), transparent 60%)' }}>
                  <Icon name="award" size={32} className="thumb-icon" />
                </div>
                <div className="card-eyebrow">{c.topic} · {c.level}</div>
                <h3>{c.title}</h3>
                <div className="card-meta">by {c.instructor}</div>
                <div className="card-footer">
                  <ProgressBar value={100} />
                  <div className="card-stats">
                    <span style={{ color: 'var(--success)' }}>Complete ✦</span>
                    <span>{c.lessons} lessons</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
