import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ThemeService } from './theme.service';
import { Theme } from '../models/theme.model';

const MOCK_THEME: Theme = {
  meta: { id: 'test-theme', name: 'Test Theme', version: '1.0.0', author: 'Test', description: 'A test theme' },
  colors: {
    backgrounds: { base: '#0a0a0a', blur: 'rgba(10,10,10,0.8)', layer_1: 'rgba(20,20,22,0.6)', panel: 'rgba(28,28,30,0.75)' },
    borders: { dim: 'rgba(255,255,255,0.06)', mid: 'rgba(255,255,255,0.12)', bright: 'rgba(255,255,255,0.2)' },
    text: { main: 'rgba(255,255,255,0.87)', dim: 'rgba(255,255,255,0.5)', muted: 'rgba(255,255,255,0.35)' },
    accents: { primary: '#ff9f0a', dim: 'rgba(255,159,10,0.12)' },
    status: { success: '#30d158', warning: '#ff9f0a', danger: '#ff453a' },
  },
  typography: { fonts: { primary: 'Inter', mono: 'JetBrains Mono' }, sizes: { base: '14px', sm: '12px', lg: '16px', xl: '20px', xxl: '24px' } },
  layout: { radii: { panel: '12px', button: '8px', badge: '6px' }, sidebar_width: '260px', header_height: '64px' },
  effects: { glass_blur: '10px', glass_saturation: '1.2', transition_speed: '200ms' },
  assets: { background_image: '', logo: '' },
};

const MOCK_CATALOG = [
  { id: 'test-theme', name: 'Test Theme', description: 'A test theme', path: 'assets/themes/test-theme.json5', preview_color: '#ff9f0a' }
];

describe('ThemeService', () => {
  let service: ThemeService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ThemeService],
    });
    service = TestBed.inject(ThemeService);
    httpMock = TestBed.inject(HttpTestingController);
    localStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should have null currentTheme initially', () => {
    expect(service.currentTheme()).toBeNull();
  });

  it('applyTheme() should set currentTheme signal', () => {
    service.applyTheme(MOCK_THEME);
    expect(service.currentTheme()).toEqual(MOCK_THEME);
  });

  it('applyTheme() should set currentThemeId signal', () => {
    service.applyTheme(MOCK_THEME);
    expect(service.currentThemeId()).toBe('test-theme');
  });

  it('applyTheme() should write --accent CSS var to document root', () => {
    service.applyTheme(MOCK_THEME);
    const value = document.documentElement.style.getPropertyValue('--accent');
    expect(value).toBe('#ff9f0a');
  });

  it('applyTheme() should write --bg-panel CSS var to document root', () => {
    service.applyTheme(MOCK_THEME);
    const value = document.documentElement.style.getPropertyValue('--bg-panel');
    expect(value).toBe('rgba(28,28,30,0.75)');
  });

  it('applyTheme() should bridge --card shadcn var', () => {
    service.applyTheme(MOCK_THEME);
    const value = document.documentElement.style.getPropertyValue('--card');
    expect(value).toBe('rgba(28,28,30,0.75)');
  });

  it('applyTheme() should bridge --primary shadcn var to accent color', () => {
    service.applyTheme(MOCK_THEME);
    const value = document.documentElement.style.getPropertyValue('--primary');
    expect(value).toBe('#ff9f0a');
  });

  it('loadTheme() should fetch JSON5 file and call applyTheme()', async () => {
    const json5Text = `${JSON.stringify(MOCK_THEME)}`;
    const loadPromise = service.loadTheme('assets/themes/test-theme.json5');

    const req = httpMock.expectOne('assets/themes/test-theme.json5');
    expect(req.request.method).toBe('GET');
    req.flush(json5Text);

    await loadPromise;
    expect(service.currentTheme()?.meta.id).toBe('test-theme');
  });

  it('loadTheme() should persist theme ID to localStorage', async () => {
    const json5Text = JSON.stringify(MOCK_THEME);
    const loadPromise = service.loadTheme('assets/themes/test-theme.json5');

    const req = httpMock.expectOne('assets/themes/test-theme.json5');
    req.flush(json5Text);
    await loadPromise;

    expect(localStorage.getItem('robin-theme')).toBe('test-theme');
  });

  it('loadFromUrl() should persist remote URL to localStorage', async () => {
    const url = 'https://example.com/theme.json5';
    const json5Text = JSON.stringify(MOCK_THEME);
    const loadPromise = service.loadFromUrl(url);

    const req = httpMock.expectOne(url);
    req.flush(json5Text);
    await loadPromise;

    expect(localStorage.getItem('robin-theme-url')).toBe(url);
    expect(localStorage.getItem('robin-theme')).toBe('test-theme');
  });

  it('listBundledThemes() should fetch index.json5 and return catalogue', async () => {
    const catalogText = JSON.stringify(MOCK_CATALOG);
    const listPromise = service.listBundledThemes();

    const req = httpMock.expectOne('assets/themes/index.json5');
    req.flush(catalogText);

    const result = await listPromise;
    expect(result.length).toBe(1);
    expect(result[0].id).toBe('test-theme');
  });

  it('loadSavedTheme() should load saved theme ID from localStorage', async () => {
    localStorage.setItem('robin-theme', 'test-theme');
    const json5Text = JSON.stringify(MOCK_THEME);
    const loadPromise = service.loadSavedTheme();

    // listBundledThemes() fires in the background — handle it
    const indexReq = httpMock.match('assets/themes/index.json5');
    indexReq.forEach(r => r.flush('[]'));

    const themeReq = httpMock.expectOne('assets/themes/test-theme.json5');
    themeReq.flush(json5Text);

    await loadPromise;
    expect(service.currentTheme()?.meta.id).toBe('test-theme');
  });

  it('loadSavedTheme() should prefer saved URL over theme ID', async () => {
    localStorage.setItem('robin-theme-url', 'https://cdn.example.com/theme.json5');
    localStorage.setItem('robin-theme', 'some-other-theme');
    const json5Text = JSON.stringify(MOCK_THEME);
    const loadPromise = service.loadSavedTheme();

    const indexReqs = httpMock.match('assets/themes/index.json5');
    indexReqs.forEach(r => r.flush('[]'));

    const urlReq = httpMock.expectOne('https://cdn.example.com/theme.json5');
    urlReq.flush(json5Text);

    await loadPromise;
    expect(service.currentTheme()?.meta.id).toBe('test-theme');
  });
});
