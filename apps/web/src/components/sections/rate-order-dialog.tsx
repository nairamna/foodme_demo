import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { foodmeApi } from "@/api/foodme";
import { ApiRequestError } from "@/api/client";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/components/ui/dialog";
import { StarRatingInput } from "@/components/sections/star-rating";
import type { FullOrderDto, OrderListResponseDto } from "@/types";

const MAX_COMMENT = 1000;

interface RateOrderDialogProps {
  order: FullOrderDto;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/** KAN-9: rate a delivered order with 1-5 stars and an optional comment. */
export function RateOrderDialog({ order, open, onOpenChange }: RateOrderDialogProps) {
  const queryClient = useQueryClient();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");

  const submit = useMutation({
    mutationFn: () =>
      foodmeApi.reviewMyOrder(order.number, {
        rating,
        comment: comment.trim() || undefined,
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData(["order", order.number], updated);
      // Show the stars in My Orders right away, then refetch to stay in sync.
      queryClient.setQueriesData<OrderListResponseDto>({ queryKey: ["my-orders"] }, (old) =>
        old && {
          ...old,
          list: old.list.map((o) => (o.number === updated.number ? updated : o)),
        },
      );
      void queryClient.invalidateQueries({ queryKey: ["my-orders"] });
      // The chef's rating changed, so storefront chef cards and pages refetch.
      void queryClient.invalidateQueries({ queryKey: ["chefs"] });
      void queryClient.invalidateQueries({ queryKey: ["chef"] });
      onOpenChange(false);
    },
  });

  const error =
    submit.error instanceof ApiRequestError
      ? submit.error.message
      : submit.error
        ? "Your rating couldn’t be saved. Please try again."
        : null;

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (submit.isPending) return;
        if (!next) {
          submit.reset();
          setRating(0);
          setComment("");
        }
        onOpenChange(next);
      }}
    >
      <DialogContent className="p-6">
        <DialogTitle className="pr-10 font-display text-xl font-bold text-zinc-900">
          Rate order {order.number}
        </DialogTitle>
        <DialogDescription className="mt-1 text-sm text-zinc-500">
          How was your order from {order.chefName}?
        </DialogDescription>

        <form
          className="mt-5 space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            if (rating > 0) submit.mutate();
          }}
        >
          <StarRatingInput value={rating} onChange={setRating} disabled={submit.isPending} />

          <div>
            <label htmlFor="order-review-comment" className="mb-2 block text-sm font-semibold text-zinc-800">
              Comment <span className="font-normal text-zinc-400">(optional)</span>
            </label>
            <textarea
              id="order-review-comment"
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              maxLength={MAX_COMMENT}
              rows={4}
              disabled={submit.isPending}
              placeholder="Tell us about your order"
              className="w-full resize-none rounded-xl border border-zinc-200 px-3 py-2 text-sm text-zinc-900 placeholder:text-zinc-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-zinc-900"
            />
            <p className="mt-1 text-right text-xs tabular-nums text-zinc-400">
              {comment.length}/{MAX_COMMENT}
            </p>
          </div>

          {error && (
            <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
              {error}
            </p>
          )}

          <Button type="submit" className="w-full" disabled={rating === 0 || submit.isPending}>
            {submit.isPending ? "Submitting…" : "Submit rating"}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
