import React, { FormEvent } from 'react';
import { Button } from '../common/Button';
import { StorySortOrder } from '../../types/api';
import './SearchForm.css';

interface SearchFormProps {
  formState: {
    query: string;
    author: string;
    minScore: number | undefined;
    sortOrder: StorySortOrder;
  };
  updateField: <K extends keyof any>(field: K, value: any) => void;
  onSearch: () => void;
  onReset: () => void;
  loading: boolean;
}

export const SearchForm: React.FC<SearchFormProps> = ({
  formState,
  updateField,
  onSearch,
  onReset,
  loading,
}) => {
  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    onSearch();
  };

  return (
    <form className="search-form" onSubmit={handleSubmit}>
      <div className="search-form__header">
        <h2>Search</h2>
      </div>

      <div className="search-form__field">
        <label htmlFor="search-query" className="search-form__label">
          Search Query
        </label>
        <input
          id="search-query"
          type="text"
          className="search-form__input"
          placeholder="Enter keywords..."
          value={formState.query}
          onChange={(e) => updateField('query', e.target.value)}
          disabled={loading}
        />
        <span className="search-form__hint">
          Search in titles and content
        </span>
      </div>

      <div className="search-form__field">
        <label htmlFor="search-author" className="search-form__label">
          Author
        </label>
        <input
          id="search-author"
          type="text"
          className="search-form__input"
          placeholder="Username..."
          value={formState.author}
          onChange={(e) => updateField('author', e.target.value)}
          disabled={loading}
        />
      </div>

      <div className="search-form__field">
        <label htmlFor="search-min-score" className="search-form__label">
          Minimum Score
        </label>
        <input
          id="search-min-score"
          type="number"
          className="search-form__input"
          placeholder="0"
          min="0"
          value={formState.minScore ?? ''}
          onChange={(e) => updateField('minScore', e.target.value ? parseInt(e.target.value, 10) : undefined)}
          disabled={loading}
        />
      </div>

      <div className="search-form__field">
        <label htmlFor="search-sort" className="search-form__label">
          Sort By
        </label>
        <select
          id="search-sort"
          className="search-form__select"
          value={formState.sortOrder}
          onChange={(e) => updateField('sortOrder', e.target.value as StorySortOrder)}
          disabled={loading}
        >
          <option value={StorySortOrder.SCORE}>Highest Score</option>
          <option value={StorySortOrder.RECENT}>Most Recent</option>
          <option value={StorySortOrder.COMMENTS}>Most Comments</option>
        </select>
      </div>

      <div className="search-form__actions">
        <Button
          type="submit"
          variant="primary"
          fullWidth
          loading={loading}
          disabled={loading}
        >
          Search
        </Button>
        <Button
          type="button"
          variant="secondary"
          fullWidth
          onClick={onReset}
          disabled={loading}
        >
          Reset
        </Button>
      </div>
    </form>
  );
};