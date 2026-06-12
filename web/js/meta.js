// Shared display metadata for task enums (status / priority / visibility).
// Returns [label, cssClass] for badges, [label, cssClass, iconClass] for visibility.
const TaskMeta = (() => {
  const STATUS = {
    NOT_STARTED: ['未着手', 'st-NOT_STARTED'],
    IN_PROGRESS: ['進行中', 'st-IN_PROGRESS'],
    DONE: ['完了', 'st-DONE'],
    ON_HOLD: ['保留', 'st-ON_HOLD'],
  };
  const PRIORITY = {
    HIGH: ['高', 'pri-HIGH'],
    MEDIUM: ['中', 'pri-MEDIUM'],
    LOW: ['低', 'pri-LOW'],
  };
  const VISIBILITY = {
    TENANT: ['テナント', 'vis-TENANT', 'bi-globe2'],
    STAKEHOLDERS: ['関係者', 'vis-STAKEHOLDERS', 'bi-people-fill'],
    PRIVATE: ['非公開', 'vis-PRIVATE', 'bi-lock-fill'],
  };

  return {
    status: (s) => STATUS[s] ?? [s, 'st-NOT_STARTED'],
    priority: (p) => PRIORITY[p] ?? [p, 'pri-LOW'],
    visibility: (v) => VISIBILITY[v] ?? [v, 'vis-TENANT', 'bi-globe2'],
    STATUS_OPTIONS: Object.keys(STATUS).map((v) => ({ v, l: STATUS[v][0] })),
    PRIORITY_OPTIONS: Object.keys(PRIORITY).map((v) => ({ v, l: PRIORITY[v][0] })),
  };
})();
