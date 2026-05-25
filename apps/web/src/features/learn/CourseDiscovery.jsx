import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import { keepPreviousData } from '@tanstack/react-query';
import Icon from '../../components/Icon';
import Modal from '../../components/Modal';
import Notification from '../../components/Notification';
import Pagination from '../../components/Pagination';
import courseService from '../../services/courseService';
import enrolmentService from '../../services/enrolmentService';
import { getErrorMessage } from '../../utils/errorMessages';
import { SkeletonCourseCard } from '../../components/Skeleton';
import useDebounce from '../../hooks/useDebounce';

export default function CourseDiscovery() {
  const navigate = useNavigate();
  const [filter, setFilter]             = useState('All');
  const [search, setSearch]             = useState('');
  const [page, setPage]                 = useState(0);
  const [requestModal, setRequestModal] = useState(null);
  const [enrollCode, setEnrollCode]     = useState('');
  const [codeError, setCodeError]       = useState('');
  const [toast, setToast]               = useState('');

  const debouncedSearch = useDebounce(search, search ? 350 : 0);

  const { data: categories = [] } = useQuery({
    queryKey: ['courses', 'categories'],
    queryFn:  () => courseService.list({ size: 200 }),
    select:   (d) => [...new Set((d.items ?? []).map(c => c.category).filter(Boolean))],
    staleTime: 5 * 60 * 1000,
  });

  const { data: pageData, isLoading, isError, error } = useQuery({
    queryKey: ['courses', 'list', { page, q: debouncedSearch.trim() || undefined, category: filter !== 'All' ? filter : undefined }],
    queryFn: () => {
      const params = {
        size: 12,
        page,
        ...(debouncedSearch.trim() ? { q: debouncedSearch.trim() } : {}),
        ...(filter !== 'All' ? { category: filter } : {}),
      };
      return courseService.list(params);
    },
    placeholderData: keepPreviousData,
  });

  const courses    = pageData?.items ?? [];
  const totalPages = pageData?.totalPages ?? 1;

  const enrolMutation = useMutation({
    mutationFn: ({ courseId, code }) => enrolmentService.enrol(courseId, code),
    onSuccess: (_, { courseId }) => {
      setRequestModal(null);
      setEnrollCode('');
      navigate(`/learn/courses/${courseId}`);
    },
    onError: (err) => setCodeError(getErrorMessage(err)),
  });

  const handleRequest = () => {
    if (!enrollCode.trim()) { setCodeError('Please enter an enrolment code.'); return; }
    setCodeError('');
    enrolMutation.mutate({ courseId: requestModal.id, code: enrollCode.trim() });
  };

  return (
    <div className="main">
      <div className="page-eyebrow">Course catalogue</div>
      <h1 className="page-title">Find your next course.</h1>
      <p className="page-lede">Short, well-paced, made by people who've done the work.</p>

      {/* Search bar */}
      <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 20 }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 8,
          background: '#fff', border: '1px solid var(--rule)',
          borderRadius: 8, padding: '8px 14px', flex: 1, maxWidth: 360,
        }}>
          <Icon name="search" size={15} color="var(--ink-3)" />
          <input
            value={search}
            onChange={e => { setSearch(e.target.value); setPage(0); }}
            placeholder="Search courses…"
            style={{ border: 0, outline: 0, background: 'transparent', font: 'inherit', fontSize: 14, flex: 1, color: 'var(--ink)' }}
          />
        </div>
      </div>

      {/* Category chips */}
      {categories.length > 0 && (
        <div className="filter-row">
          {['All', ...categories].map(t => (
            <span
              key={t}
              className={`chip${filter === t ? ' active' : ''}`}
              onClick={() => { setFilter(t); setPage(0); }}
            >
              {t}
            </span>
          ))}
        </div>
      )}

      {isLoading && (
        <div className="course-grid">{[0, 1, 2].map(i => <SkeletonCourseCard key={i} />)}</div>
      )}

      {isError && (
        <p style={{ color: 'var(--danger)', textAlign: 'center', padding: '60px 0' }}>
          {getErrorMessage(error)}
        </p>
      )}

      {!isLoading && !isError && courses.length === 0 && (
        <p style={{ color: 'var(--ink-3)', textAlign: 'center', padding: '60px 0' }}>
          {!search && filter === 'All' ? 'No published courses yet.' : 'No courses match your search.'}
        </p>
      )}

      {!isLoading && !isError && courses.length > 0 && (
        <div className="course-grid">
          {courses.map(c => (
            <div key={c.id} className="course-card" onClick={() => navigate(`/learn/courses/${c.id}`)}>
              <div className="thumb">
                <Icon name="book-open" size={32} className="thumb-icon" />
                {c.visibility === 'PRIVATE' && (
                  <span className="thumb-badge">
                    <Icon name="lock" size={11} color="#fff" /> Private
                  </span>
                )}
              </div>
              <div className="card-body">
                <div className="card-eyebrow">{c.category || 'General'}</div>
                <h3>{c.title}</h3>
                <p style={{ fontSize: 13, color: 'var(--ink-3)', margin: 0, lineHeight: 1.5 }}>
                  {c.description}
                </p>
                <div className="card-footer">
                  <button
                    className={`btn btn-sm ${c.visibility === 'PRIVATE' ? 'btn-secondary' : 'btn-primary'}`}
                    style={{ alignSelf: 'flex-start' }}
                    onClick={e => {
                      e.stopPropagation();
                      c.visibility === 'PRIVATE' ? setRequestModal(c) : navigate(`/learn/courses/${c.id}`);
                    }}
                  >
                    {c.visibility === 'PRIVATE' ? 'Request access' : 'View course'}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />

      {requestModal && (
        <Modal
          title="Enter enrolment code"
          onClose={() => { setRequestModal(null); setEnrollCode(''); setCodeError(''); }}
          actions={
            <>
              <button className="btn btn-secondary" onClick={() => { setRequestModal(null); setEnrollCode(''); setCodeError(''); }}>Cancel</button>
              <button className="btn btn-primary" onClick={handleRequest} disabled={enrolMutation.isPending}>
                {enrolMutation.isPending ? 'Enrolling…' : 'Enrol'}
              </button>
            </>
          }
        >
          <p>
            Enter the enrolment code to get immediate access to{' '}
            <strong>{requestModal.title}</strong>.
          </p>
          <div className="field">
            <label>Enrolment code</label>
            <input
              className="input"
              value={enrollCode}
              onChange={e => { setEnrollCode(e.target.value); setCodeError(''); }}
              placeholder="e.g. LEARN-2026-XY"
              style={{ fontFamily: 'var(--font-mono)' }}
              autoFocus
            />
            {codeError && <p style={{ fontSize: 13, color: 'var(--danger)', marginTop: 6, marginBottom: 0 }}>{codeError}</p>}
          </div>
        </Modal>
      )}

      {toast && <Notification>{toast}</Notification>}
    </div>
  );
}