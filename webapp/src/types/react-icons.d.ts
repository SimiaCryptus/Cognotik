import * as React from 'react';

export interface IconBaseProps extends React.SVGProps<SVGSVGElement> {
  size?: string | number;
  color?: string;
  className?: string;
}

// Define the icon type to accept ReactNode
export type IconType = React.ComponentType<IconBaseProps>;