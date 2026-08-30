/**
 * React Router's v7 opt-ins, in one place.
 *
 * Router 6.30 prints a console warning for every future flag left unset, which trains
 * everybody to ignore the console — and the two flags below are behaviour this app either
 * already wants or is unaffected by. `v7_startTransition` wraps route state updates in
 * `React.startTransition`, so a navigation that suspends does not blank the page it is
 * leaving. `v7_relativeSplatPath` fixes relative link resolution inside splat routes;
 * the only splat here is the `NotFound` catch-all, which contains no relative links, so
 * opting in costs nothing today and removes a migration step later.
 *
 * Both flag names were checked against the installed version rather than copied from a
 * blog post: `logV6DeprecationWarnings` in `react-router-dom` warns on exactly these two.
 */
export const ROUTER_FUTURE = {
  v7_startTransition: true,
  v7_relativeSplatPath: true,
}
