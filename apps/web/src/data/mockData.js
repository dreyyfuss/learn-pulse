export const MOCK_USERS = {
  alex:  { id: 'u1', name: 'Alex Reyes',  email: 'alex@example.com',  roles: ['LEARNER', 'INSTRUCTOR'] },
  marta: { id: 'u2', name: 'Marta Olsen', email: 'marta@example.com', roles: ['INSTRUCTOR'] },
  jamie: { id: 'u3', name: 'Jamie Ng',    email: 'jamie@example.com', roles: ['LEARNER'] },
};

export const COURSES = [
  {
    id: 'c1', title: 'Data structures, in plain English',
    instructor: 'Marta Olsen', topic: 'Computer science', level: 'Intermediate',
    blurb: 'Twelve evenings, no jargon. Lists, trees, hash maps — and when to reach for each.',
    modules: 3, lessons: 12, enrolled: 624, completions: 443, completionRate: 71,
    status: 'published', visibility: 'public',
    progress: 33, lessonsDone: 4, minsLeft: 48,
  },
  {
    id: 'c2', title: 'A quiet course on layout',
    instructor: 'Marta Olsen', topic: 'Design', level: 'Beginner',
    blurb: 'Grids, alignment, type. The bones of every interface.',
    modules: 2, lessons: 8, enrolled: 418, completions: 347, completionRate: 83,
    status: 'published', visibility: 'public',
    progress: 100, lessonsDone: 8, minsLeft: 0,
  },
  {
    id: 'c3', title: 'Plain language at work',
    instructor: 'Marta Olsen', topic: 'Writing', level: 'Beginner',
    blurb: 'Write emails people actually finish reading.',
    modules: 3, lessons: 9, enrolled: 242, completions: 126, completionRate: 52,
    status: 'published', visibility: 'private',
    progress: 12, lessonsDone: 1, minsLeft: 72,
  },
  {
    id: 'c4', title: 'Algorithms without the panic',
    instructor: 'Marta Olsen', topic: 'Computer science', level: 'Advanced',
    blurb: 'Big-O, sort, search. Calmly.',
    modules: 4, lessons: 14, enrolled: 0, completions: 0, completionRate: 0,
    status: 'draft', visibility: 'private',
    progress: 0, lessonsDone: 0, minsLeft: 0,
  },
];

export const MODULES_DATA = [
  { id: 'm1', title: 'Module 1 — Pointers and lists', locked: false, lessons: [
    { id: 'l1', idx: 1, title: 'What is a pointer, really?', mins: 6, done: true, type: 'video' },
    { id: 'l2', idx: 2, title: 'Walking a list, step by step', mins: 8, done: true, type: 'video' },
    { id: 'l3', idx: 3, title: 'When lists beat arrays', mins: 7, done: true, type: 'reading' },
    { id: 'l4', idx: 4, title: 'Drawing it on paper', mins: 5, done: true, type: 'reading' },
  ]},
  { id: 'm2', title: 'Module 2 — Linked lists in practice', locked: false, lessons: [
    { id: 'l5', idx: 1, title: 'Inserting at the head', mins: 5, done: false, current: true, type: 'video' },
    { id: 'l6', idx: 2, title: 'Inserting in the middle', mins: 7, done: false, type: 'video' },
    { id: 'l7', idx: 3, title: 'Removing a node', mins: 6, done: false, type: 'reading' },
    { id: 'l8', idx: 4, title: 'A short exercise', mins: 10, done: false, type: 'exercise' },
  ]},
  { id: 'm3', title: 'Module 3 — Trees', locked: true, lessons: [
    { id: 'l9',  idx: 1, title: 'A list with branches', mins: 6, done: false, type: 'video',   locked: true },
    { id: 'l10', idx: 2, title: 'Walking a tree', mins: 8, done: false, type: 'reading', locked: true },
    { id: 'l11', idx: 3, title: 'Tree traversal patterns', mins: 9, done: false, type: 'video',   locked: true },
  ]},
];

export const CERTIFICATES = [
  { id: 'cert1', courseTitle: 'A quiet course on layout', instructor: 'Marta Olsen', completedDate: '4 May 2026', certId: 'cert_8f2a·b41c·9d77·2026' },
  { id: 'cert2', courseTitle: 'Introduction to typography', instructor: 'Sam Chen', completedDate: '14 Mar 2026', certId: 'cert_3a1b·c55f·2e88·2026' },
];

export const LEARNER_TABLE = [
  { id: 'l1', name: 'Alex Reyes', email: 'alex@example.com', status: 'in-progress', currentLesson: 'M02·L01 Inserting at the head', enrolled: '1 Apr 2026', completed: '' },
  { id: 'l2', name: 'Jamie Ng',   email: 'jamie@example.com', status: 'completed',   currentLesson: 'M03·L03 Tree traversal patterns', enrolled: '10 Feb 2026', completed: '28 Apr 2026' },
  { id: 'l3', name: 'Priya Shah', email: 'priya@example.com', status: 'in-progress', currentLesson: 'M02·L03 Removing a node', enrolled: '3 Mar 2026', completed: '' },
  { id: 'l4', name: 'Tom Willis', email: 'tom@example.com',   status: 'in-progress', currentLesson: 'M01·L04 Drawing it on paper', enrolled: '18 Apr 2026', completed: '' },
  { id: 'l5', name: 'Yuki Tanaka',email: 'yuki@example.com', status: 'completed',   currentLesson: 'M03·L03 Tree traversal patterns', enrolled: '5 Jan 2026', completed: '12 Apr 2026' },
  { id: 'l6', name: 'Carlos Ruiz',email: 'carlos@example.com',status: 'in-progress', currentLesson: 'M01·L02 Walking a list', enrolled: '22 Apr 2026', completed: '' },
];

export const ADMIN_USERS = [
  { id: 'au1', name: 'Alex Reyes', email: 'alex@example.com', roles: ['learner', 'instructor'], status: 'active', registered: '12 Jan 2026' },
  { id: 'au2', name: 'Marta Olsen', email: 'marta@example.com', roles: ['instructor'], status: 'active', registered: '3 Nov 2025' },
  { id: 'au3', name: 'Jamie Ng', email: 'jamie@example.com', roles: ['learner'], status: 'active', registered: '20 Feb 2026' },
  { id: 'au4', name: 'Priya Shah', email: 'priya@example.com', roles: ['learner'], status: 'active', registered: '3 Mar 2026' },
  { id: 'au5', name: 'Tom Willis', email: 'tom@example.com', roles: ['learner'], status: 'suspended', registered: '18 Apr 2026' },
  { id: 'au6', name: 'Sam Chen', email: 'sam@example.com', roles: ['instructor'], status: 'active', registered: '8 Aug 2025' },
  { id: 'au7', name: 'Yuki Tanaka', email: 'yuki@example.com', roles: ['learner'], status: 'active', registered: '5 Jan 2026' },
];

export const ADMIN_COURSES = [
  { id: 'c1', title: 'Data structures, in plain English', instructor: 'Marta Olsen', visibility: 'public', status: 'published', enrolled: 624, completions: 443 },
  { id: 'c2', title: 'A quiet course on layout', instructor: 'Marta Olsen', visibility: 'public', status: 'published', enrolled: 418, completions: 347 },
  { id: 'c3', title: 'Plain language at work', instructor: 'Marta Olsen', visibility: 'private', status: 'published', enrolled: 242, completions: 126 },
  { id: 'c4', title: 'Algorithms without the panic', instructor: 'Marta Olsen', visibility: 'private', status: 'draft', enrolled: 0, completions: 0 },
  { id: 'c5', title: 'Introduction to typography', instructor: 'Sam Chen', visibility: 'public', status: 'published', enrolled: 189, completions: 134 },
  { id: 'c6', title: 'CSS layout patterns', instructor: 'Sam Chen', visibility: 'public', status: 'locked', enrolled: 312, completions: 201 },
];
