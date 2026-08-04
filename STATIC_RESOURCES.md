# Static Resources Setup

This document describes the static resources (CSS, JS, images) that have been added to the Curling Masters project.

## Directory Structure

```
src/main/resources/static/
├── css/
│   └── main.css          # Main stylesheet
├── js/
│   └── main.js           # Main JavaScript file
├── images/
│   └── README.md         # Instructions for image usage
└── ...                   # Additional static files can be added here
```

## Files Added

### 1. CSS File (`/static/css/main.css`)
- **Purpose**: Main stylesheet for the application
- **Features**:
  - Global styles and theme colors
  - Navigation styling
  - Button and card enhancements
  - Form validation styles
  - Responsive design
  - Animation classes (fade-in, slide-in)
  - Mobile-friendly adjustments

### 2. JavaScript File (`/static/js/main.js`)
- **Purpose**: Main JavaScript functionality
- **Features**:
  - DOM initialization
  - Animation system
  - Form validation
  - Navigation enhancements
  - Bootstrap tooltips
  - Alert system
  - Loading spinners
  - API helper functions
  - Date/time formatting
  - Global utility functions

### 3. CORS Configuration (`WebConfig.java`)
- **Purpose**: Handle cross-origin requests and static resource serving
- **Features**:
  - CORS support for all origins, methods, and headers
  - Static resource handlers for CSS, JS, and images
  - Cache configuration for better performance

## Template Updates

All HTML templates have been updated to include the new static resources:

- `home.html` - Updated with main.css and main.js
- `login.html` - Updated with main.css and main.js
- `register.html` - Updated with main.css and main.js
- `reset-password.html` - Updated with main.css and main.js
- `user-edit.html` - Updated with main.css and main.js
- `user-manage.html` - Updated with main.css and main.js

## Usage in Templates

### CSS Reference
```html
<link th:href="@{/css/main.css}" rel="stylesheet">
```

### JavaScript Reference
```html
<script th:src="@{/js/main.js}"></script>
```

### Image Reference
```html
<img th:src="@{/images/your-image.png}" alt="Description">
```

## CORS Configuration

The application is configured to handle cross-origin requests:

- **Allowed Origins**: All origins (using `allowedOriginPatterns("*")`)
- **Allowed Methods**: GET, POST, PUT, DELETE, OPTIONS
- **Allowed Headers**: All headers
- **Credentials**: Supported
- **Max Age**: 3600 seconds (1 hour)

## Testing

Static resources can be tested at:
- CSS: `http://localhost:8080/css/main.css`
- JS: `http://localhost:8080/js/main.js`
- Images: `http://localhost:8080/images/your-image.png`

## Adding New Resources

### Adding New CSS Files
1. Create file in `src/main/resources/static/css/`
2. Reference in templates: `<link th:href="@{/css/your-file.css}" rel="stylesheet">`

### Adding New JS Files
1. Create file in `src/main/resources/static/js/`
2. Reference in templates: `<script th:src="@{/js/your-file.js}"></script>`

### Adding Images
1. Place image in `src/main/resources/static/images/`
2. Reference in templates: `<img th:src="@{/images/your-image.png}" alt="Description">`

## Browser Compatibility

The CSS and JS files are designed to work with modern browsers:
- Chrome 60+
- Firefox 55+
- Safari 12+
- Edge 79+

## Performance Features

- CSS and JS files are cached for 1 hour
- Resource chains are enabled for optimization
- Gzip compression is handled by Spring Boot
- Static resources are served efficiently

## Security Features

- X-Content-Type-Options: nosniff
- X-Frame-Options: DENY
- X-XSS-Protection: 0
- Proper CORS headers
- Content Security Policy ready structure
