import { createBrowserRouter, Navigate } from 'react-router-dom';
import Layout from '../components/layout/Layout';
import { Home } from '../pages/Home';
import { NewStories } from '../pages/NewStories';
import { StoryDetail } from '../pages/StoryDetail';
import { Search } from '../pages/Search';
import { UserProfile } from '../pages/UserProfile';
import { NotFound } from '../pages/NotFound';

/**
 * Application router configuration
 * Defines all routes and their corresponding components
 */
export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    errorElement: <NotFound />,
    children: [
      {
        index: true,
        element: <Home />,
      },
      {
        path: 'new',
        element: <NewStories />,
      },
      {
        path: 'story/:id',
        element: <StoryDetail />,
      },
      {
        path: 'search',
        element: <Search />,
      },
      {
        path: 'user/:username',
        element: <UserProfile />,
      },
      {
        path: '404',
        element: <NotFound />,
      },
      {
        path: '*',
        element: <Navigate to="/404" replace />,
      },
    ],
  },
]);

/**
 * Route path constants for type-safe navigation
 */
export const ROUTES = {
  HOME: '/',
  NEW: '/new',
  STORY: (id: number | string) => `/story/${id}`,
  SEARCH: '/search',
  USER: (username: string) => `/user/${username}`,
  NOT_FOUND: '/404',
} as const;