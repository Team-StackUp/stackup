import { Suspense, type ReactNode, type ComponentProps } from 'react';
import { ErrorBoundary } from './ErrorBoundary';

interface AsyncBoundaryProps extends Omit<ComponentProps<typeof ErrorBoundary>, 'fallback'> {
  pendingFallback: ReactNode;
  rejectedFallback: ComponentProps<typeof ErrorBoundary>['fallback']; 
  children: ReactNode; 
}

export const AsyncBoundary = ({
  pendingFallback,
  rejectedFallback,
  children,
  ...errorBoundaryProps
}: AsyncBoundaryProps) => {
  return (
    <ErrorBoundary fallback={rejectedFallback} {...errorBoundaryProps}>
      <Suspense fallback={pendingFallback}>
        {children}
      </Suspense>
    </ErrorBoundary>
  );
};