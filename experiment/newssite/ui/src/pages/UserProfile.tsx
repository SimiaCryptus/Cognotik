import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useUser, useUserStories, useUserComments } from '../hooks/useUsers';
import { usePagination } from '../hooks/usePagination';
import { UserInfo } from '../components/users/UserInfo';
import { UserStats } from '../components/users/UserStats';
import { UserActivity } from '../components/users/UserActivity';
import { Loading } from '../components/common/Loading';
import { Error } from '../components/common/Error';
import './UserProfile.css';

type ActivityTab = 'stories' | 'comments';

export const UserProfile: React.FC = () => {
  const { username } = useParams<{ username: string }>();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<ActivityTab>('stories');

  const { data: user, loading: userLoading, error: userError, refetch: refetchUser } = useUser(username || null);

  const {
    page: storiesPage,
    pageSize: storiesPageSize,
    goToPage: setStoriesPage,
  } = usePagination();

  const {
    page: commentsPage,
    pageSize: commentsPageSize,
    goToPage: setCommentsPage,
  } = usePagination();

  const {
    data: storiesData,
    loading: storiesLoading,
    error: storiesError,
    refetch: refetchStories,
  } = useUserStories(username || null, storiesPage, storiesPageSize);

  const {
    data: commentsData,
    loading: commentsLoading,
    error: commentsError,
    refetch: refetchComments,
  } = useUserComments(username || null, commentsPage, commentsPageSize);

  if (userLoading) {
    return <Loading size="large" text="Loading user profile..." />;
  }

  if (userError) {
    return (
      <div className="user-profile-error">
        <Error
          message={userError.message || 'Failed to load user profile'}
          onRetry={refetchUser}
        />
      </div>
    );
  }

  if (!user) {
    return (
      <div className="user-profile-error">
        <Error
          message="User not found"
          onRetry={() => navigate('/')}
          retryText="Go Home"
        />
      </div>
    );
  }

  const handleTabChange = (tab: ActivityTab) => {
    setActiveTab(tab);
    if (tab === 'stories') {
      setStoriesPage(1);
    } else {
      setCommentsPage(1);
    }
  };

  return (
    <div className="user-profile">
      <div className="user-profile__header">
        <UserInfo user={user} />
        <UserStats user={user} />
      </div>

      <div className="user-profile__activity">
        <div className="user-profile__tabs">
          <button
            className={`user-profile__tab ${activeTab === 'stories' ? 'user-profile__tab--active' : ''}`}
            onClick={() => handleTabChange('stories')}
          >
            Stories ({user.submittedIds.length})
          </button>
          <button
            className={`user-profile__tab ${activeTab === 'comments' ? 'user-profile__tab--active' : ''}`}
            onClick={() => handleTabChange('comments')}
          >
            Comments
          </button>
        </div>

        <div className="user-profile__content">
          {activeTab === 'stories' && (
            <UserActivity
              type="stories"
              data={storiesData}
              loading={storiesLoading}
              error={storiesError}
              currentPage={storiesPage}
              onPageChange={setStoriesPage}
              onRetry={refetchStories}
            />
          )}

          {activeTab === 'comments' && (
            <UserActivity
              type="comments"
              data={commentsData}
              loading={commentsLoading}
              error={commentsError}
              currentPage={commentsPage}
              onPageChange={setCommentsPage}
              onRetry={refetchComments}
            />
          )}
        </div>
      </div>
    </div>
  );
};