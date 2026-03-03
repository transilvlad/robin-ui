import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { DmarcApiService } from '../../services/dmarc-api.service';
import { DmarcPolicyAdvice, DmarcRecommendation } from '../../models/dmarc.models';

interface ActionStep {
  icon: string;
  text: string;
}

@Component({
  selector: 'app-dmarc-policy-advisor',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dmarc-policy-advisor.component.html',
})
export class DmarcPolicyAdvisorComponent implements OnInit, OnDestroy {
  private readonly api    = inject(DmarcApiService);
  private readonly route  = inject(ActivatedRoute);
  private readonly destroy$ = new Subject<void>();

  loading = true;
  domain  = '';
  advice: DmarcPolicyAdvice | null = null;
  error:  string | null = null;

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntil(this.destroy$)).subscribe(params => {
      this.domain = params.get('domain') ?? '';
      this.load();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    this.loading = true;
    this.error   = null;
    this.api.getPolicyAdvice(this.domain).pipe(takeUntil(this.destroy$)).subscribe(result => {
      this.loading = false;
      if (result.ok) {
        this.advice = result.value;
      } else {
        this.error = 'Failed to load policy advice.';
      }
    });
  }

  // ── Traffic-light styling ─────────────────────────────────────────────────

  get recommendation(): DmarcRecommendation { return this.advice?.recommendation ?? 'stay'; }

  get trafficLightColor(): string {
    const map: Record<DmarcRecommendation, string> = {
      advance: 'border-green-500  bg-green-50  dark:bg-green-900/20',
      stay:    'border-orange-400 bg-orange-50 dark:bg-orange-900/10',
      caution: 'border-red-500   bg-red-50    dark:bg-red-900/20',
    };
    return map[this.recommendation];
  }

  get indicatorClass(): string {
    const map: Record<DmarcRecommendation, string> = {
      advance: 'bg-green-500',
      stay:    'bg-orange-400',
      caution: 'bg-red-500',
    };
    return map[this.recommendation];
  }

  get recommendationLabel(): string {
    const map: Record<DmarcRecommendation, string> = {
      advance: '✓ Ready to Advance',
      stay:    '→ Hold Current Policy',
      caution: '⚠ Caution — Review Required',
    };
    return map[this.recommendation];
  }

  get recommendationTextClass(): string {
    const map: Record<DmarcRecommendation, string> = {
      advance: 'text-green-700 dark:text-green-400',
      stay:    'text-orange-700 dark:text-orange-400',
      caution: 'text-red-700   dark:text-red-400',
    };
    return map[this.recommendation];
  }

  get actionSteps(): ActionStep[] {
    const r = this.recommendation;
    const pct = this.advice?.compliancePercent ?? 0;

    if (r === 'advance') {
      return [
        { icon: '🛡️', text: 'Move DMARC policy from p=none to p=quarantine for this domain.' },
        { icon: '📧', text: 'Monitor for 7–14 days after policy change; watch for legitimate failures.' },
        { icon: '🔒', text: 'Once quarantine is stable (≥95% for 30 days), advance to p=reject.' },
      ];
    }
    if (r === 'stay') {
      return [
        { icon: '📊', text: `Current compliance is ${pct.toFixed(1)}%. Aim for ≥95% before advancing.` },
        { icon: '🔍', text: 'Review Sources page to identify any legitimate senders failing DMARC.' },
        { icon: '🔑', text: 'Ensure DKIM keys are published and aligned for all sending services.' },
        { icon: '📬', text: 'Verify SPF records include all authorized mail servers.' },
      ];
    }
    // caution
    return [
      { icon: '🚨', text: `Compliance is critically low (${pct.toFixed(1)}%). Do NOT tighten policy yet.` },
      { icon: '📋', text: 'Use DMARC Reports to identify which senders are failing authentication.' },
      { icon: '✉️', text: 'Check for misconfigured SPF records (missing IPs, too many lookups).' },
      { icon: '🔑', text: 'Verify DKIM is properly configured for all mail-sending services.' },
      { icon: '⏳', text: 'Remain at p=none until compliance exceeds 75% consistently.' },
    ];
  }
}
